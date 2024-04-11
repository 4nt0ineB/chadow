package fr.uge.chadow.client;


import fr.uge.chadow.core.context.ClientContext;
import fr.uge.chadow.core.protocol.Request;
import fr.uge.chadow.core.protocol.RequestDownload;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.YellMessage;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
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
  private final String login;
  private final CodexController codexController = new CodexController();
  private final ArrayList<YellMessage> publicMessages = new ArrayList<>();
  private final HashMap<UUID, DirectMessages> directMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final ArrayBlockingQueue<Optional<String>> requestCodexResponseQueue = new ArrayBlockingQueue<>(1);
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition connectionCondition = lock.newCondition();
  private ClientContext clientContext;
  private STATUS status = STATUS.CONNECTING;
  
  public ClientAPI(String login) {
    Objects.requireNonNull(login);
    this.login = login;
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
  
  public void waitForConnection() throws InterruptedException {
    lock.lock();
    try {
      while (status.equals(STATUS.CONNECTING)) {
        connectionCondition.await();
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
    var codex = codexController.getCodexStatus(id)
                               .codex();
    logger.info(STR."(propose) codex \{codex.name()} (id: \{codex.id()}) queued");
    clientContext.queueFrame(codex);
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
    if (codex != null) {
      return Optional.of(codex);
    }
    requestCodexResponseQueue.clear();
    clientContext.queueFrame(new Request(id));
    var fetchedCodexId = requestCodexResponseQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    if (fetchedCodexId == null) {
      return Optional.empty();
    }
    return fetchedCodexId.map(codexController::getCodexStatus);
  }
  
  public void whisper(UUID recipientId, String message) {
    var dm = getPrivateDiscussionByRecipientId(recipientId);
    if (dm.isEmpty()) {
      logger.warning(STR."(whisper) whispering to id \{recipientId}, but was not found");
      return;
    }
    var recipientUsername = dm.orElseThrow()
                              .username();
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
    codexController.stopDownloading(id);
    // clientContext.queueFrame(new Cancel(id));
  }
  
  public void download(String id) {
    codexController.download(id);
    clientContext.queueFrame(new RequestDownload(id, (byte) 0));
  }
  
  
  public void addIncomingDM(WhisperMessage msg) {
    var dm = getDirectMessagesOf(msg.username());
    dm.ifPresentOrElse(d -> {
      
      logger.info(STR."Adding incoming message to existing discussion with \{msg.username()}");
      d.addMessage(msg);
    }, () -> {
      var newDM = new DirectMessages(UUID.randomUUID(), msg.username());
      var startMessage = new WhisperMessage("", STR."This is the beginning of your direct message history with \{msg.username()}", System.currentTimeMillis());
      newDM.addMessage(startMessage);
      newDM.addMessage(msg);
      directMessages.put(newDM.id(), newDM);
    });
    logger.info(STR."\{msg.username()} is whispering a message of length \{msg.txt()
                                                                              .length()}");
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
    this.directMessages.put(userId, new DirectMessages(userId, "Alan1"));
    // test codex
    codexController.createFromPath("my codex", "/home/alan1/Pictures");
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
    codexController.stopSharing(codexId);
    // clientContext.queueFrame(new Cancel(codexId)); @Todo
  }
  
  public void share(String codexId) {
    codexController.share(codexId);
    //propose(codexId); @Todo
  }
  
  enum STATUS {
    CONNECTING,
    CONNECTED,
    CLOSED,
  }
  
  
}