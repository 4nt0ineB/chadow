package fr.uge.chadow.client;

import fr.uge.chadow.core.context.ClientContext;
import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.YellMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class Client {
  
  private static final Logger logger = Logger.getLogger(Client.class.getName());
  private final SocketChannel sc;
  private final Selector selector;
  private final InetSocketAddress serverAddress;
  private final String login;
  private ClientContext clientContext;
  private final HashMap<String, Codex> codexes = new HashMap<>();
  private final ArrayList<YellMessage> publicMessages = new ArrayList<>();
  private final HashMap<UUID, Recipient> privateMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final ArrayBlockingQueue<Optional<String>> requestCodexResponseQueue = new ArrayBlockingQueue<>(1);


  public Client(String login, InetSocketAddress serverAddress) throws IOException {
    this.serverAddress = serverAddress;
    this.login = login;
    this.sc = SocketChannel.open();
    this.selector = Selector.open();
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    if (args.length != 3) {
      usage();
      return;
    }

    new Client(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
  }

  private static void usage() {
    System.out.println("Usage : ClientChat login hostname port");
  }

  public String serverHostName() {
    return serverAddress.getHostName();
  }

  public String login() {
    return login;
  }

  public void launch() throws IOException {
    // for dev: fake messages
    try {
      fillWithFakeData();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    sc.configureBlocking(false);
    var key = sc.register(selector, SelectionKey.OP_CONNECT);
    clientContext = new ClientContext(key, this);
    key.attach(clientContext);
    sc.connect(serverAddress);
    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }

  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isConnectable()) {
        clientContext.doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        clientContext.doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        clientContext.doRead();
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
  }


  ////////////// CLIENT API //////////////////////////
  /// API to interact on session with the server /////
  ////////////////////////////////////////////////////

  public List<YellMessage> getPublicMessages() {
    lock.lock();
    try {
      return List.copyOf(publicMessages);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Send instructions to the selector via a BlockingQueue and wake it up
   *
   * @param msg
   */
  public void yell(String msg) {
    logger.info(STR."(yell) message queued of length \{msg.length()}");
    clientContext.queueFrame(new YellMessage(login, msg, 0L));
    selector.wakeup();
  }
  
  public void propose(Codex codex) {
    logger.info(STR."(propose) codex \{codex.name()} (id: \{codex.idToHexadecimal()}) queued");
    clientContext.queue.addFirst(codex);
    selector.wakeup();
  }
  
  public List<String> getUsers() {
    lock.lock();
    try {
      return Collections.unmodifiableList(new ArrayList<>(users));
    } finally {
      lock.unlock();
    }
  }

  /**
   * Add a codex to the client
   *
   * @param codex
   * @throws IllegalArgumentException if the codex already exists
   */
  public void addCodex(Codex codex) {
    if (codexes.containsKey(codex.name())) {
      throw new IllegalArgumentException("Codex already exists");
    }
    codexes.put(codex.idToHexadecimal(), codex);
  }

  public List<Codex> codexes() {
    return List.copyOf(codexes.values());
  }

  public Optional<Codex> getCodex(String id) throws InterruptedException {
    var codex = codexes.get(id);
    if(codex != null) {
      return Optional.of(codex);
    }
    requestCodexResponseQueue.clear();
    clientContext.queueFrame(new Request(id));
    var fetchedCodexId = requestCodexResponseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    if(fetchedCodexId == null) {
      return Optional.empty();
    }
    return fetchedCodexId.map(codexes::get);
  }
  
  public void whisper(UUID recipientId, String message){
    var recipient = getRecipientbyId(recipientId);
    if(recipient.isEmpty()) {
      logger.warning(STR."(whisper) whispering to id \{recipientId}, but was not found");
      return;
    }
    var recipientUsername = recipient.orElseThrow().username();
    clientContext.queueFrame(new WhisperMessage(recipientUsername, message, 0L));
    logger.info(STR."(whisper) message to \{recipientUsername} of length \{message.length()} queued");
    selector.wakeup();
    recipient.orElseThrow().addMessage(new WhisperMessage(login, message, System.currentTimeMillis()));
  }
  
  public Optional<Recipient> getRecipientbyId(UUID userId) {
    lock.lock();
    try {
      var recipient = privateMessages.get(userId);
      if(recipient == null) {
        return Optional.empty();
      }
      return Optional.of(recipient);
    } finally {
      lock.unlock();
    }
  }
  
  public Optional<Recipient> getRecipient(String username) {
    lock.lock();
    try {
      var recipient = privateMessages.values().stream().filter(u -> u.username().equals(username)).findFirst();
      recipient.ifPresent(r -> {
        if(!r.hasMessages()) {
          r.addMessage(new WhisperMessage("(info)", STR."This is the beginning of your private message history with \{username}", System.currentTimeMillis()));
        }
      });
      return recipient;
    } finally {
      lock.unlock();
    }
  }
  
  private void silentlyClose(SelectionKey key) {
    Channel sc = key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }
  
  
  public void stopDownloading(String id) {
    var codex = codexes.get(id);
    if(codex == null) {
      return;
    }
    codex.stopDownloading();
    // clientContext.queueFrame(new Cancel(id));
  }
  
  public void download(String id) {
    var codex = codexes.get(id);
    if(codex == null) {
      return;
    }
    codex.download();
    clientContext.queueFrame(new RequestDownload(id, (byte) 0));
  }
  

  
  
  /**
   * Check if the client is connected to the server
   *
   * @return true if the client is connected to the server, false otherwise
   */
  public boolean isConnected() {
    return clientContext.isConnected();
  }

  /////////// For the session only

  public void addMessage(YellMessage msg) {
    lock.lock();
    try {
      publicMessages.add(msg);
      logger.info(STR."Received message from \{msg.login()} : \{msg.txt()}");
    } finally {
      lock.unlock();
    }
  }

  public void addWhisper(WhisperMessage msg) {
    var Recipient = getRecipient(msg.username());
    Recipient.ifPresentOrElse(r -> r.addMessage(msg), () -> {
      var newRecipient = new Recipient(UUID.randomUUID(), msg.username());
      var startMessage = new WhisperMessage("", STR."This is the beginning of your private message history with \{msg.username()}", System.currentTimeMillis());
      newRecipient.addMessage(startMessage);
      newRecipient.addMessage(msg);
      privateMessages.put(newRecipient.id(), newRecipient);
    });
    logger.info(STR."\{msg.username()} is whispering a message of length \{msg.txt().length()}");
  }
  
  

  private void fillWithFakeData() throws IOException, NoSuchAlgorithmException {
    var users = new String[]{"test", "Morpheus", "Trinity", "Neo", "Flynn", "Alan", "Lora", "Gandalf", "Bilbo", "SKIDROW", "Antoine"};
    this.users.addAll(Arrays.asList(users));
    var messages = new YellMessage[]{
            new YellMessage("test", "test", System.currentTimeMillis()),
            new YellMessage("test", "hello how are you", System.currentTimeMillis()),
            new YellMessage("Morpheus", "Wake up, Neo...", System.currentTimeMillis()),
            new YellMessage("Morpheus", "The Matrix has you...", System.currentTimeMillis()),
            new YellMessage("Neo", "what the hell is this", System.currentTimeMillis()),
            new YellMessage("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7", System.currentTimeMillis()),
            new YellMessage("SKIDROW", "Here is the codex of the FOSS (.deb) : cdx:1eb49a28a0c02b47eed4d0b968bb9aec116a5a47", System.currentTimeMillis()),
            new YellMessage("Antoine", "Le lien vers le sujet : http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html", System.currentTimeMillis())
    };
    this.publicMessages.addAll(splashLogo());
    this.publicMessages.addAll(Arrays.asList(messages));
    var userId = UUID.randomUUID();
    this.privateMessages.put(userId, new Recipient(userId, "Alan1"));
    // test codex
    var codex = Codex.fromPath("my codex", "/home/alan1/Pictures");
    codexes.put(codex.idToHexadecimal(), codex);
    codex = Codex.fromPath("my codex", "/home/alan1/Downloads/Great Teacher Onizuka (1999)/Great Teacher Onizuka - S01E01 - Lesson 1.mkv");
    codexes.put(codex.idToHexadecimal(), codex);
  }
  
  /**
   * Create a splash screen logo with a list of messages
   * showing le title "Chadow" in ascii art and the version
   */
  public static Collection<YellMessage> splashLogo() {
    return List.of(
        new YellMessage("", "┏┓┓    ┓", 0),
        new YellMessage("", "┃ ┣┓┏┓┏┫┏┓┓┏┏", 0),
        new YellMessage("", "┗┛┗┗┗┗┗┗┗┛┗┛┛ v1.0.0 - Bastos & Sebbah", 0)
    );
  }
}