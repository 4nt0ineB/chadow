package fr.uge.chadow.client;


import fr.uge.chadow.core.context.ClientContext;
import fr.uge.chadow.core.context.DownloaderContext;
import fr.uge.chadow.core.context.SharerContext;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.YellMessage;
import fr.uge.chadow.core.protocol.client.Propose;
import fr.uge.chadow.core.protocol.client.Request;
import fr.uge.chadow.core.protocol.client.RequestDownload;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * This class is the API for the client side.
 * To interact with the session associated with the server to which the client
 * has established a connection.
 * Thread-safe.
 */
public class ClientAPI {
  
  
  private static final Logger logger = Logger.getLogger(ClientAPI.class.getName());
  private final ContextHandler contextHandler;
  private final InetSocketAddress serverAddress;
  private final String login;
  private final CodexController codexController = new CodexController();
  private final ArrayList<YellMessage> publicMessages = new ArrayList<>();
  private final HashMap<UUID, DirectMessages> directMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final ArrayBlockingQueue<Optional<Codex>> requestCodexResponseQueue = new ArrayBlockingQueue<>(1);
  
  private final LinkedBlockingQueue<SocketField[]> sharersSocketQueue = new LinkedBlockingQueue<>();
  private final ArrayDeque<String> codexIdOfAskedDownload = new ArrayDeque<>();
  
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition connectionCondition = lock.newCondition();
  private ClientContext clientContext;
  private STATUS status = STATUS.CONNECTING;
  
  
  public ClientAPI(String login, InetSocketAddress serverAddress) throws IOException {
    Objects.requireNonNull(login);
    this.login = login;
    this.serverAddress = serverAddress;
    this.contextHandler = new ContextHandler(key -> new SharerContext(key, this));
    try {
      fillWithFakeData();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
      // just die
    }
  }
  
  /**
   * Create a splash screen logo with a list of messages
   * showing le title "Chadow" in ascii art and the version
   */
  public static List<YellMessage> splashLogo() {
    return List.of(
        new YellMessage("", "┏┓┓    ┓", 0),
        new YellMessage("", "┃ ┣┓┏┓┏┫┏┓┓┏┏", 0),
        new YellMessage("", "┗┛┗┗┗┗┗┗┗┛┗┛┛ v1.0.0 - Bastos & Sebbah", 0)
    );
  }
  
  public void startService() throws InterruptedException, IOException {
    // Starts the client thread
    startClient();
    
    // make a loop to manage downloads
    while (!Thread.interrupted()) {
      var sockets = sharersSocketQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
      if (sockets != null) {
        var codexId = codexIdOfAskedDownload.pollFirst();
        // create downloader for each sharer
        for(var socket: sockets) {
          addDownloaderContext(codexId, socket);
        }
      }
    }
  }
  
  private void startClient() throws InterruptedException, IOException {
    try {
      Thread.ofPlatform()
            .daemon()
            .start(() -> {
              try {
                logger.info("Client starts");
                contextHandler.supplyConnectionData(key -> new ContextHandler.ConnectionData(new ClientContext(key, this), serverAddress));
                contextHandler.launch();
              } catch (IOException e) {
                logger.severe(STR."The client was interrupted. \{e.getMessage()}");
              }
            });
    } catch (UncheckedIOException e) {
      logger.severe(STR."The client was interrupted while starting.\{e.getCause()}");
      return;
    }
    try {
      waitForConnection();
    } catch (InterruptedException e) {
      logger.severe(STR."The client was interrupted while waiting for connection.\{e.getCause()}");
      throw e;
    }
    if (!isConnected()) {
      logger.severe("The client was not able to connect to the server.");
      
      throw new IOException();
    }
  }
  
  /**
   * Create the contexts that will download the codex
   */
  private void addDownloaderContext(String codexId, SocketField socket) {
    var codexStatus = codexController.getCodexStatus(codexId);
    if(codexStatus.isEmpty()) {
      return;
    }
    InetAddress address = null;
    try {
      address = InetAddress.getByAddress(socket.ip());
    } catch (UnknownHostException e) {
      logger.warning("Could not resolve the address of the sharer");
      return;
    }
    var add = new InetSocketAddress(address, socket.port());
    contextHandler.supplyConnectionData(key -> {
      var context = new DownloaderContext(key, this, codexStatus.orElseThrow());
      return new ContextHandler.ConnectionData(context, add);
    });
  }
  
  public void close() {
    lock.lock();
    try {
      logger.severe("Closing the client API");
      status = STATUS.CLOSED;
      connectionCondition.signalAll();
    } finally {
      lock.unlock();
    }
  }
  
  public String login() {
    lock.lock();
    try {
      if (!isLogged()) {
        throw new IllegalStateException("Not logged in");
      }
      return login;
    } finally {
      lock.unlock();
    }
  }
  
  public List<DirectMessages> discussionWithUnreadMessages() {
    lock.lock();
    try {
      return directMessages.values()
                           .stream()
                           .filter(DirectMessages::hasNewMessages)
                           .toList();
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Bind the client context to the API
   * This method is called by the context when the connection is established
   * and the client is ready to interact with the server
   * The connection is supposed to be alive when
   * a context is bound to the API
   *
   * @param context
   */
  public void bindContext(ClientContext context) {
    lock.lock();
    try {
      clientContext = context;
      connectionCondition.signalAll();
      status = STATUS.CONNECTED;
      logger.info(STR."Connection established, authenticated on the server as \{login}");
    } finally {
      lock.unlock();
    }
  }
  
  public boolean isLogged() {
    return login != null;
  }
  
  /**
   * Unbind the client context from the API
   * This method is called by the context when the connection is closed
   * and the client is no longer able to interact with the server
   */
  public void unbindContext() {
    lock.lock();
    try {
      clientContext = null;
      status = STATUS.CLOSED;
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Wait for the connection to be established and the client to be ready
   * to interact with the server.
   *
   * @throws InterruptedException if the client could not connect to the server
   */
  public void waitForConnection() throws InterruptedException {
    lock.lock();
    try {
      while (status.equals(STATUS.CONNECTING)) {
        connectionCondition.await();
      }
      if (status.equals(STATUS.CLOSED)) {
        throw new InterruptedException("Could not connect to the server");
      }
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Check if the client is connected to the server
   *
   * @return true if the client is connected to the server, false otherwise
   */
  public boolean isConnected() {
    lock.lock();
    try {
      return clientContext != null && !status.equals(STATUS.CLOSED);
    } finally {
      lock.unlock();
    }
  }
  
  public List<YellMessage> getPublicMessages() {
    lock.lock();
    try {
      return Collections.unmodifiableList(publicMessages);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Send instructions to the selector via a BlockingQueue and wake it up
   *
   * @param msg
   */
  public void sendPublicMessage(String msg) {
    lock.lock();
    try {
      logger.info(STR."(yell) message queued of length \{msg.length()}");
      clientContext.queueFrame(new YellMessage(login, msg, 0L));
    } finally {
      lock.unlock();
    }
  }
  
  
  public void propose(String id) {
    codexController.getCodexStatus(id)
                   .ifPresent(codexStatus -> {
                     var codex = codexStatus.codex();
                     logger.info(STR."(propose) codex \{codex.name()} (id: \{codex.id()}) queued");
                     clientContext.queueFrame(new Propose(codex));
                   });
  }
  
  public List<String> users() {
    lock.lock();
    try {
      return Collections.unmodifiableList(new ArrayList<>(users));
    } finally {
      lock.unlock();
    }
  }
  
  
  /**
   * Add a codex to the client
   */
  public CodexController.CodexStatus addCodex(String name, String path) throws IOException, NoSuchAlgorithmException {
    return codexController.createFromPath(name, path);
  }
  
  public List<CodexController.CodexStatus> codexes() {
    return List.copyOf(codexController.codexesStatus());
  }
  
  public Optional<CodexController.CodexStatus> getCodex(String id) throws InterruptedException {
    var codex = codexController.getCodexStatus(id);
    if (codex.isPresent()) {
      return Optional.of(codex.orElseThrow());
    }
    // didn't find the codex, request it
    requestCodexResponseQueue.clear();
    clientContext.queueFrame(new Request(id));
    logger.info(STR."(getCodex) requesting codex (id: \{id})");
    var fetchedCodex = requestCodexResponseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    if (fetchedCodex == null || fetchedCodex.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(codexController.fromFetchedCodex(fetchedCodex.orElseThrow(), "/tmp"));
  }
  
  public void saveFetchedCodex(Codex codex) {
    lock.lock();
    try {
      try {
        requestCodexResponseQueue.put(Optional.of(codex));
      } catch (InterruptedException e) {
        close();
      }
    } finally {
      lock.unlock();
    }
  }
  
  public void whisper(UUID recipientId, String message) {
    var dm = getPrivateDiscussionByRecipientId(recipientId);
    if (dm.isEmpty()) {
      logger.warning(STR."(whisper) whispering to id \{recipientId}, but was not found");
      return;
    }
    var recipientUsername = dm.orElseThrow()
                              .username();
    if (!users.contains(recipientUsername)) {
      logger.warning(STR."(whisper) whispering to \{recipientUsername}, but this user is not connected");
      return;
    }
    clientContext.queueFrame(new WhisperMessage(recipientUsername, message, 0L));
    logger.info(STR."(whisper) message to \{recipientUsername} of length \{message.length()} queued");
    dm.orElseThrow()
      .addMessage(new WhisperMessage(login, message, System.currentTimeMillis()));
  }
  
  public Optional<DirectMessages> getPrivateDiscussionByRecipientId(UUID recipientId) {
    lock.lock();
    try {
      var recipient = directMessages.get(recipientId);
      if (recipient == null) {
        return Optional.empty();
      }
      return Optional.of(recipient);
    } finally {
      lock.unlock();
    }
  }
  
  public List<DirectMessages> getAllDirectMessages() {
    lock.lock();
    try {
      return directMessages.values()
                           .stream()
                           .filter(DirectMessages::hasMessages)
                           .toList();
    } finally {
      lock.unlock();
    }
  }
  
  public Optional<DirectMessages> getDirectMessagesOf(String username) {
    lock.lock();
    try {
      var recipient = directMessages.values()
                                    .stream()
                                    .filter(u -> u.username()
                                                  .equals(username))
                                    .findFirst();
      recipient.ifPresent(r -> {
        if (!r.hasMessages()) {
          r.addMessage(new WhisperMessage("(info)", STR."This is the beginning of your direct message history with \{username}", System.currentTimeMillis()));
        }
      });
      if (recipient.isEmpty()) {
        var dm = DirectMessages.create(username);
        directMessages.put(dm.id(), dm);
        return Optional.of(dm);
      }
      return recipient;
    } finally {
      lock.unlock();
    }
  }
  
  public void addMessage(YellMessage msg) {
    lock.lock();
    try {
      publicMessages.add(msg);
      logger.info(STR."Received message from \{msg.login()} : \{msg.txt()}");
    } finally {
      lock.unlock();
    }
  }
  
  public void stopDownloading(String id) {
    lock.lock();
    try {
      codexController.stopDownloading(id);
      // clientContext.queueFrame(new Cancel(id));
    } finally {
      lock.unlock();
    }
  }
  
  public void download(String id) {
    lock.lock();
    try {
      codexController.download(id);
      clientContext.queueFrame(new RequestDownload(id, (byte) 0));
      codexIdOfAskedDownload.addLast(id);
    } finally {
      lock.unlock();
    }
  }
  
  public void addIncomingDM(WhisperMessage msg) {
    var dm = getDirectMessagesOf(msg.username());
    dm.ifPresentOrElse(d -> {
      logger.info(STR."Adding incoming message to existing discussion with \{msg.username()}");
      d.addMessage(msg);
    }, () -> {
      var newDM = DirectMessages.create(msg.username());
      directMessages.put(newDM.id(), newDM);
    });
    logger.info(STR."\{msg.username()} is whispering a message of length \{msg.txt().length()}");
  }
  
  public int numberOfMessages() {
    lock.lock();
    try {
      return publicMessages.size();
    } finally {
      lock.unlock();
    }
  }
  
  public int totalUsers() {
    lock.lock();
    try {
      return users.size();
    } finally {
      lock.unlock();
    }
  }
  
  public boolean isDownloading(String codexId) {
    return codexController.isDownloading(codexId);
  }
  
  public boolean isSharing(String codexId) {
    return codexController.isSharing(codexId);
  }
  
  public void stopSharing(String codexId) {
    lock.lock();
    try {
      codexController.stopSharing(codexId);
      // clientContext.queueFrame(new Cancel(codexId)); @Todo
    } finally {
      lock.unlock();
    }
  }
  
  public void share(String codexId) {
    codexController.share(codexId);
    propose(codexId);
  }
  
  public void addUser(String username) {
    lock.lock();
    try {
      users.add(username);
      addMessage(new YellMessage("-->", STR."\{username} has joined", System.currentTimeMillis()));
    } finally {
      lock.unlock();
    }
  }
  
  public void removeUser(String username) {
    lock.lock();
    try {
      users.remove(username);
      getDirectMessagesOf(username)
          .ifPresent(dm -> {
            var random = new Random();
            var newUsername = STR."\{dm.username()}[\{random.nextInt(1000)}]";
            dm.changeUsername(newUsername);
            dm.addMessage(new WhisperMessage("<--", STR."User \{username} left", System.currentTimeMillis()));
            dm.addMessage(new WhisperMessage("<--", STR."\{username} renamed as \{newUsername}", System.currentTimeMillis()));
          });
      addMessage(new YellMessage("<--", STR."\{username} left", System.currentTimeMillis()));
    } finally {
      lock.unlock();
    }
  }
  
  public void addUserFromDiscovery(List<String> usernames) {
    lock.lock();
    try {
      users.addAll(usernames);
    } finally {
      lock.unlock();
    }
  }
  
  public int listeningPort() {
    return contextHandler.listeningPort();
  }
  
  public void addSocketsOpenDownload(SocketField[] sockets) {
    sharersSocketQueue.add(sockets);
  }
  
  enum STATUS {
    CONNECTING,
    CONNECTED,
    CLOSED,
  }
  
  private void fillWithFakeData() throws IOException, NoSuchAlgorithmException {
    //var users = new String[]{"test", "Morpheus", "Trinity", "Neo", "Flynn", "Alan", "Lora", "Gandalf", "Bilbo", "SKIDROW", "Antoine"};
    //this.users.addAll(Arrays.asList(users));
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
    //var userId = UUID.randomUUID();
    //this.directMessages.put(userId, new DirectMessages(userId, "Alan1"));
    // test codex
    if (login.equals("Alan1")) {
      codexController.createFromPath("my codex", "/home/alan1/Pictures");
    }
  }
}