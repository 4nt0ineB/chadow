package fr.uge.chadow.client;


import fr.uge.chadow.core.context.*;
import fr.uge.chadow.core.TCPConnectionManager;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.YellMessage;
import fr.uge.chadow.core.protocol.client.Propose;
import fr.uge.chadow.core.protocol.client.Request;
import fr.uge.chadow.core.protocol.client.RequestDownload;
import fr.uge.chadow.core.protocol.client.Search;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.ProxyNodeSocket;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.SearchResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * This class is the API for the client side.
 * To interact with the session associated with the server to which the client
 * has established a connection.
 * Thread-safe.
 */
public class ClientAPI {
  
  private static final Logger logger = Logger.getLogger(ClientAPI.class.getName());
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition connectionCondition = lock.newCondition();
  private final CodexController codexController;
  private final InetSocketAddress serverAddress;
  private final String login;
  private final ArrayList<YellMessage> publicMessages = new ArrayList<>();
  private final HashMap<UUID, DirectMessages> directMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  
  // Blocking Queue that will contain the fetched codex
  private final long REQUEST_CODEX_TIMEOUT = 5; // todo add in settings
  private final ArrayBlockingQueue<Optional<Codex>> requestCodexResponseQueue = new ArrayBlockingQueue<>(1);
  
  // Manage request and answer of open download -- Maybe change the way to handle this
  private final long DOWNLOAD_REQUEST_TIMEOUT = 5; // todo add in settings
  private final LinkedBlockingQueue<SocketResponse> sharersSocketQueue = new LinkedBlockingQueue<>();
  private final ArrayDeque<String> codexIdOfAskedDownload = new ArrayDeque<>();
  private final HashMap<String, Integer> downloaders = new HashMap<>();
  
  // Manage request and response of search
  private final long SEARCH_TIMEOUT = 5; // todo add in settings
  private final ArrayBlockingQueue<SearchResponse> searchResponseQueue = new ArrayBlockingQueue<>(1);
  
  // The context handler that will manage the client contexts
  private TCPConnectionManager connectionManager;
  private ClientContext clientContext;
  private STATUS status = STATUS.CONNECTING;
  
  // Proxy
  private final HashMap<Integer, SocketField> proxyRoutes = new HashMap<>();
  
  public boolean saveProxyRoute(int chainId, SocketField socket) {
    return proxyRoutes.putIfAbsent(chainId, socket) == null;
  }
  
  public boolean setUpBridge(int chainId, ClientAsServerContext clientAsServerContext) {
    var socket = proxyRoutes.get(chainId);
    if(socket == null) {
      return false;
    }
    addContext(socket, key -> new ProxyBridgeContext(key, clientAsServerContext));
    return true;
  }
  
  /**
   * Register a downloader context.
   * Increment the number of downloaders for the codex
   * @param id the id of the codex
   */
  public void registerDownloader(String id) {
    lock.lock();
    try {
      downloaders.compute(id, (k, v) -> v == null ? 1 : v + 1);
    } finally {
      lock.unlock();
    }
  }
  
  public void unregisterDownloader(String id) {
    lock.lock();
    try {
      downloaders.computeIfPresent(id, (k, v) -> Math.max(0, v - 1));
    } finally {
      lock.unlock();
    }
  }
  
  private record SocketResponse(SocketField[] sockets, int[] chainId) {
    public SocketResponse {
      if (chainId != null && chainId.length != chainId.length) {
        throw new IllegalArgumentException("The number of chain ids must be equal to the number of sockets");
      }
    }
  }
  
  public ClientAPI(String login, InetSocketAddress serverAddress, CodexController codexController) {
    Objects.requireNonNull(login);
    this.login = login;
    this.serverAddress = serverAddress;
    this.codexController = codexController;
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
    this.connectionManager = new TCPConnectionManager(0, key -> new ClientAsServerContext(key, this));
    // Starts the client thread
    startClient();
    waitForConnection();
    try {
      fillWithFakeData();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
      // just die
    }
    // make a loop to manage downloads
    while (!Thread.interrupted() && status.equals(STATUS.CONNECTED)) {
      var socketResponse = sharersSocketQueue.poll(DOWNLOAD_REQUEST_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
      if (socketResponse != null) {
        var codexId = codexIdOfAskedDownload.pollFirst();
        codexController.createFileTree(codexId);
        // create downloader for each sharer
        for(var i = 0;  i < socketResponse.sockets.length; i++) {
          var socket = socketResponse.sockets[i];
          var chainId = socketResponse.chainId != null ? socketResponse.chainId[i] : null;
          logger.info(STR."New downloader context for codex \{codexId} (sharer: \{socket.ip()}:\{socket.port()}) (hidden: \{chainId != null})");
          addDownloaderContext(codexId, socket, chainId);
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
                connectionManager.supplyConnectionData(key -> new TCPConnectionManager.ConnectionData(new ClientContext(key, this), serverAddress));
                connectionManager.launch();
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
   * @param codexId the id of the codex
   * @param socket the socket of the sharer
    * @param chainId the chain id of the download - may be null if the download is not hidden
   */
  private void addDownloaderContext(String codexId, SocketField socket, Integer chainId) {
    var codexStatus = codexController.getCodexStatus(codexId);
    if(codexStatus.isEmpty()) {
      return;
    }
    addContext(socket, key -> new DownloaderContext(key, this, codexStatus.orElseThrow(), chainId));
  }
  
  public void addContext(SocketField socket, Function<SelectionKey, Context> contextSupplier) {
    InetAddress address;
    try {
      address = InetAddress.getByAddress(socket.ip());
    } catch (UnknownHostException e) {
      logger.warning("Could not resolve the address of the sharer");
      return;
    }
    var addr = new InetSocketAddress(address, socket.port());
    connectionManager.supplyConnectionData(key -> {
      var context = contextSupplier.apply(key);
      return new TCPConnectionManager.ConnectionData(context, addr);
    });
  }
  
  public void close() {
    lock.lock();
    try {
      logger.severe("Closing the client API");
      status = STATUS.CLOSED;
      codexController.close();
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
   * @param context the client context
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
      return List.copyOf(publicMessages);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Send instructions to the selector via a BlockingQueue and wake it up
   *
   * @param msg the message to send
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
      return List.copyOf(users);
    } finally {
      lock.unlock();
    }
  }
  
  
  /**
   * Add a codex to the client
   */
  public CodexStatus addCodex(String name, String path) throws IOException, NoSuchAlgorithmException {
    return codexController.createFromPath(name, path);
  }
  
  public List<CodexStatus> codexes() {
    return List.copyOf(codexController.codexesStatus());
  }
  
  public Optional<CodexStatus> getCodex(String id) {
    var codex = codexController.getCodexStatus(id);
    if (codex.isPresent()) {
      return Optional.of(codex.orElseThrow());
    }
    // didn't find the codex, request it
    requestCodexResponseQueue.clear();
    clientContext.queueFrame(new Request(id));
    logger.info(STR."(getCodex) requesting codex (id: \{id})");
    Optional<Codex> fetchedCodex = null;
    try {
      fetchedCodex = requestCodexResponseQueue.poll(REQUEST_CODEX_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.severe(e.getMessage());
      close();
    }
    if (fetchedCodex == null || fetchedCodex.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(codexController.addFromFetchedCodex(fetchedCodex.orElseThrow()));
  }
  
  public void saveFetchedCodex(Codex codex) {
    lock.lock();
    try {
      requestCodexResponseQueue.put(Optional.of(codex));
    } catch (InterruptedException e) {
        close();
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
  
  /**
   * Download a codex
   * @param id the id of the codex
   * @param hidden if the codex must be downloaded in hidden mode
   */
  public void download(String id, boolean hidden) {
    lock.lock();
    try {
      codexController.download(id, hidden);
      // TODO : change the way to handle this
      clientContext.queueFrame(new RequestDownload(id, (byte) (hidden ? 1 : 0), 10, 1));
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
  
  public int howManySharers(String codexId) {
    lock.lock();
    try {
      return downloaders.getOrDefault(codexId, 0);
    } finally {
      lock.unlock();
    }
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
  
  public boolean codexExists(String id) {
    return codexController.codexExists(id);
  }
  
  /**
   * Get a chunk of a codex
   * @param wantedCodexId the id of the codex
   * @param offset the offset of the chunk
   * @param length the length of the chunk
   * @return the chunk of data
   * @throws IllegalArgumentException if the codex does not exist
   */
  public byte[] getChunk(String wantedCodexId, long offset, int length) throws IOException {
    lock.lock();
    try {
      if(!codexController.codexExists(wantedCodexId)) {
        throw new IllegalArgumentException("The codex does not exist");
      }
      return codexController.getChunk(wantedCodexId, offset, length);
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
            var randomUUID = UUID.randomUUID();
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
    return connectionManager.listeningPort();
  }
  
  public void addSocketsOpenDownload(SocketField[] sockets) {
    sharersSocketQueue.add(new SocketResponse(sockets, null));
  }
  
  public void addSocketsClosedDownload(ProxyNodeSocket[] proxySockets) {
    var sockets = new SocketField[proxySockets.length];
    var chainId = new int[proxySockets.length];
    for (int i = 0; i < proxySockets.length; i++) {
      sockets[i] = proxySockets[i].socket();
      chainId[i] = proxySockets[i].chainId();
    }
    sharersSocketQueue.add(new SocketResponse(sockets, chainId));
  }
  
  /**
   * Write a chunk of data in a codex
   * @param id the id of the codex
   * @param offset the offset of the chunk
   * @param payload the data to write
   * @throws IOException if the codex does not exist
   */
  public void writeChunk(String id, long offset, byte[] payload) throws IOException {
    lock.lock();
    try {
      codexController.writeChunk(id, offset, payload);
    } finally {
      lock.unlock();
    }
  }
  
  
  public Optional<SearchResponse> searchCodexes(Search search) {
    try {
      searchResponseQueue.clear();
      clientContext.queueFrame(search);
      logger.info(STR."(searchCodexes) searching for codexes with name \{search.codexName()}");
      SearchResponse response = null;
      response = searchResponseQueue.poll(SEARCH_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
      if (response == null) {
        return Optional.empty();
      }
      return Optional.of(response);
    } catch (InterruptedException e) {
      close();
      return Optional.empty();
    }
  }
  
  public void saveSearchResponse(SearchResponse response) {
    lock.lock();
    try {
      searchResponseQueue.put(response);
    } catch (InterruptedException e) {
      close();
    } finally {
      lock.unlock();
    }
  }
  
  
  enum STATUS {
    CONNECTING,
    CONNECTED,
    CLOSED,
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
        new YellMessage("Antoine", "Le lien vers le sujet : https://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html", System.currentTimeMillis())
    };
    this.publicMessages.addAll(splashLogo());
    this.publicMessages.addAll(Arrays.asList(messages));
    //var userId = UUID.randomUUID();
    //this.directMessages.put(userId, new DirectMessages(userId, "Alan1"));
    // test codex
    if (login.equals("Alan1")) {
      //var status = codexController.createFromPath("my codex", "/mnt/d/Photos/DSC00003.JPG");
      //var status = codexController.createFromPath("test", "/home/alan1/Documents/tmp/tablette");
      var status = codexController.createFromPath("test", "/home/alan1/Pictures");
      share(status.id());
      /*var paths = new String[]{
          "lau-ardelean-wallpaper",
          "lau-ardelean-wallpaper/Ephemeral-Echoes-Landscape.jpg",
          "lau-ardelean-wallpaper/Lonely-Wavelengths-Landscape.jpg",
          "lau-ardelean-wallpaper/the-beauty-of-identity-and-circuitry-v0-3q6rocbvslib1.jpg",
          "lau-ardelean-wallpaper/Intersecting-Human-Form-Landscape.jpg",
          "lau-ardelean-wallpaper/The-Tree-Landscape.jpg",
          "lau-ardelean-wallpaper/Luminous-Horizon-Landscape.jpg",
          "lau-ardelean-wallpaper/Fluidity-and-Repetition-in-the-Bay-Landscape.jpg",
          "lau-ardelean-wallpaper/Branches-of-Restraint-Ladscape.jpg",
          "lau-ardelean-wallpaper/Retro-Ascent-Landscape.jpg",
          "lau-ardelean-wallpaper/Lone-Island-Landscape.jpg",
          "lau-ardelean-wallpaper/Stormy-Seascape-Feather-Landscape.jpg",
          "lau-ardelean-wallpaper/Dark-Sunset-Landscape.jpg",
          "CV_Bastos_Antoine.pdf",
          "Screenshot from 2024-04-16 01-10-38.png",
          "questions-reponses.pdf",
          "Screenshot from 2024-04-14 22-58-42.png",
          "STScI-01HM9W90RSHFHAEAW5FKGVJCTH.png",
          "test",
          "test/2.txt",
          "test/1.txt",
          "test/Black-Panthers-in-Chicago-010.avif",
          "Black-Panthers-in-Chicago-010.avif",
          "Black-Panthers-in-Chicago-010.jpg",
          "smplayer_screenshots",
          "Screenshot from 2023-11-10 22-27-56.png",
          "170189868287888.jpeg",
          "Screenshot from 2023-10-11 20-52-51.png",
          "an7qgBo_700b.jpg",
          "Screenshot from 2023-10-11 18-11-22.png"
      };
      var random = new Random();
      for (var path : paths) {
        var status = codexController.createFromPath(STR."my codex (\{random.nextInt(100)})", "/home/alan1/Pictures/" + path);
        share(status.id());
      }*/
      //codexController.createFromPath("my codex", "/home/alan1/Pictures/test");
      
    }
  }
}