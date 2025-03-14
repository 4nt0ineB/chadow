package fr.uge.chadow.client;


import fr.uge.chadow.core.Settings;
import fr.uge.chadow.core.ProxyManager;
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
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition connectionCondition = lock.newCondition();
  private final CodexController codexController;
  private final InetSocketAddress serverAddress;
  private final ArrayList<YellMessage> publicMessages = new ArrayList<>();
  private final HashMap<UUID, DirectMessages> directMessages = new HashMap<>();
  private final SortedSet<String> users = new TreeSet<>();
  private final Settings settings;
  private final ProxyManager proxyManager = new ProxyManager();

  // Blocking Queue that will contain the fetched codex
  private final ArrayBlockingQueue<Optional<Codex>> requestCodexResponseQueue = new ArrayBlockingQueue<>(1);

  // Manage request and answer of open download -- Maybe change the way to handle this
  private final LinkedBlockingQueue<SocketResponse> sharersSocketQueue = new LinkedBlockingQueue<>();
  private final ArrayDeque<String> codexIdOfAskedDownload = new ArrayDeque<>();
  private final HashMap<String, Set<InetSocketAddress>> currentDownloads = new HashMap<>();
  private final HashMap<String, Integer> currentSharing = new HashMap<>();
  private int proxyiedConnection = 0;

  // Manage request and response of search
  private final ArrayBlockingQueue<SearchResponse> searchResponseQueue = new ArrayBlockingQueue<>(1);

  // The context handler that will manage the client contexts
  private TCPConnectionManager connectionManager;
  private ClientContext clientContext;
  private STATUS status = STATUS.CONNECTING;

  public ClientAPI(InetSocketAddress serverAddress, CodexController codexController, Settings settings) {
    this.serverAddress = serverAddress;
    this.codexController = codexController;
    this.settings = settings;
  }

  public void startService() throws InterruptedException, IOException {
    this.connectionManager = new TCPConnectionManager(0, key -> new ClientAsServerContext(key, this, settings.getInt("maxAcceptedChunkSize") * 1024));
    // Starts the client thread
    startConnectionManagerThread();
    waitForConnection();
    this.publicMessages.addAll(splashLogo());
    if (settings.getBool("debug")) {
      try {
        fillWithFakeData();
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (NoSuchAlgorithmException e) {
        // just die
        throw new RuntimeException(e);
      }
    }
    Thread.ofPlatform().daemon().start(this::periodicSocketRequest);
    startDownloaderRunner();
  }

  private void startConnectionManagerThread() throws InterruptedException, IOException {
    try {
      Thread.ofPlatform()
              .daemon()
              .start(() -> {
                try {
                  logger.info("Client context starts");
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
   * Start the downloader runner.
   * This method will wait for sockets requested by the client to download a codex.
   * When sockets is received, will create a downloader context for each socket
   *
   * @throws IOException          if an I/O error occurs when creating the codex file tree
   * @throws InterruptedException if the client was interrupted while waiting for the sockets
   */
  private void startDownloaderRunner() throws IOException, InterruptedException {
    while (!Thread.interrupted() && status.equals(STATUS.CONNECTED)) {
      var socketResponse = sharersSocketQueue.poll(settings.getInt("downloadRequestTimeout"), java.util.concurrent.TimeUnit.SECONDS);
      if (socketResponse != null) {
        var codexId = codexIdOfAskedDownload.pollFirst();
        if (!currentDownloads.containsKey(codexId)) {
          codexController.createFileTree(codexId);
          currentDownloads.put(codexId, new HashSet<>());
        }
        var sockets = currentDownloads.get(codexId);
        // create downloader for each sharer
        for (var i = 0; i < socketResponse.sockets.length; i++) {
          var socketField = socketResponse.sockets[i];
          var socketAddress = new InetSocketAddress(InetAddress.getByAddress(socketField.ip()), socketField.port());
          
          if (sockets.contains(socketAddress)) {
            continue; // already downloading from this sharer
          }
          var chainId = socketResponse.chainId != null ? socketResponse.chainId[i] : null;
          logger.info(STR."New downloader context for codex \{codexId} (sharer: \{socketField.ip()}:\{socketField.port()}) (hidden: \{chainId != null})");
          addDownloaderContext(codexId, socketField, chainId);
          sockets.add(socketAddress);
        }
      }
    }
  }

  /**
   * Try to improve a download by requesting more sockets
   * each minute
   */
  private void periodicSocketRequest() {
    var timeout = 1000 * settings.getInt("newSocketRequestTimeout");
    while (!Thread.interrupted() && status.equals(STATUS.CONNECTED)) {
      try {
        Thread.sleep(timeout);
        logger.info("Requesting more sockets for current downloads");
      } catch (InterruptedException e) {
        logger.severe(STR."The client was interrupted while sleeping.\{e.getCause()}");
        return;
      }
      var toRemove = new HashSet<String>();
      for (var codexId : currentDownloads.keySet()) {
        var codexStatus = codexController.getCodexStatus(codexId);
        if (codexStatus.isPresent() && codexStatus.orElseThrow().isComplete()) {
          toRemove.add(codexId);
          continue;
        }
        var downloadIsHidden = codexController.getCodexStatus(codexId).orElseThrow().isDownloadingHidden();
        requestSocketForDownload(codexId, downloadIsHidden, settings.getInt("proxyChainSize"));
      }
      toRemove.forEach(currentDownloads::remove);
    }
  }


  public boolean saveProxyRoute(int chainId, SocketField socket) {
    return proxyManager.saveProxyRoute(chainId, socket);
  }

  public boolean setUpBridge(int chainId, ClientAsServerContext clientAsServerContext) {
    var socket = proxyManager.getNextHopSocket(chainId);
    if (socket.isEmpty()) {
      return false;
    }
    connectionManager.addContext(socket.orElseThrow(), key -> new ProxyBridgeRightSideContext(key, clientAsServerContext));
    return true;
  }

  /**
   * Register a downloader context.
   * Update the list of sharers for the codex
   *
   * @param codexId the id of the codex
   */
  public void registerDownloader(String codexId, InetSocketAddress sharerAddress) {
    lock.lock();
    try {
      if(sharerAddress == null) {
        return;
      }
      currentDownloads.computeIfAbsent(codexId, k -> new HashSet<>())
              .add(sharerAddress);
    } finally {
      lock.unlock();
    }
  }

  public void unregisterDownloader(String codexId, InetSocketAddress sharerAddress) {
    lock.lock();
    try {
      currentDownloads.computeIfPresent(codexId, (k, v) -> {
        v.remove(sharerAddress);
        return v;
      });
    } finally {
      lock.unlock();
    }
  }

  public int howManyDownloaders(String codexId) {
    lock.lock();
    try {
      logger.info(STR."NUMBER OF DOWNLOADERS : \{currentDownloads.getOrDefault(codexId, Set.of())
              .size()}");
      logger.info(STR."DISPLAY OF DOWNLOADERS : \{currentDownloads.getOrDefault(codexId, Set.of())}");
      return currentDownloads.getOrDefault(codexId, Set.of()).size();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Register a sharer context.
   * Update the list of sharers for the codex
   *
   * @param codexId the id of the codex
   */
  public void registerSharer(String codexId) {
    lock.lock();
    try {
      currentSharing.putIfAbsent(codexId, 0);
      currentSharing.compute(codexId, (k, v) -> v + 1);
    } finally {
      lock.unlock();
    }
  }

  public void unregisterSharer(String codexId) {
    lock.lock();
    try {
      currentSharing.computeIfPresent(codexId, (k, v) -> Math.max(0, v - 1));
    } finally {
      lock.unlock();
    }
  }

  public int howManySharers(String codexId) {
    lock.lock();
    try {
      return currentSharing.getOrDefault(codexId, 0);
    } finally {
      lock.unlock();
    }
  }

  public void registerProxy() {
    lock.lock();
    try {
      proxyiedConnection++;
    } finally {
      lock.unlock();
    }
  }

  public void unregisterProxy() {
    lock.lock();
    try {
      proxyiedConnection = Math.max(0, proxyiedConnection - 1);
    } finally {
      lock.unlock();
    }
  }

  public int howManyProxy() {
    lock.lock();
    try {
      return proxyiedConnection;
    } finally {
      lock.unlock();
    }
  }


  public void deleteDirectMessagesWith(UUID id) {
    lock.lock();
    try {
      directMessages.remove(id);
    } finally {
      lock.unlock();
    }
  }

  private record SocketResponse(SocketField[] sockets, int[] chainId) {
  }

  /**
   * Create the contexts that will download the codex
   *
   * @param codexId the id of the codex
   * @param socket  the socket of the sharer
   * @param chainId the chain id of the download - may be null if the download is not hidden
   */
  private void addDownloaderContext(String codexId, SocketField socket, Integer chainId) {
    var codexStatus = codexController.getCodexStatus(codexId);
    if (codexStatus.isEmpty()) {
      return;
    }
    connectionManager.addContext(socket, key -> new DownloaderContext(key, this, codexStatus.orElseThrow(), chainId));
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
      return settings.getStr("login");
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
              .sorted(Comparator.comparing(DirectMessages::id))
              .toList();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Bind the client context to the API.
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
      logger.info(STR."Connection established, authenticated on the server as \{settings.getStr("login")}");
    } finally {
      lock.unlock();
    }
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
      clientContext.queueFrame(new YellMessage(settings.getStr("login"), msg, 0L));
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
  
  public Optional<CodexStatus> getCodex(String codexId) {
    var codex = codexController.getCodexStatus(codexId);
    if (codex.isPresent()) {
      return Optional.of(codex.orElseThrow());
    }
    // didn't find the codex, request it
    requestCodexResponseQueue.clear();
    clientContext.queueFrame(new Request(codexId));
    logger.info(STR."(getCodex) requesting codex (id: \{codexId})");
    Optional<Codex> fetchedCodex = null;
    try {
      fetchedCodex = requestCodexResponseQueue.poll(settings.getInt("requestCodexTimeout"), java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.severe(e.getMessage());
      close();
    }
    if (fetchedCodex == null || fetchedCodex.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(codexController.addFromFetchedCodex(fetchedCodex.orElseThrow()));
  }
  
  /**
   * Get the codex id or the first guess if the codex does not exist
   *
   * @param codexId the id of the codex
   * @return the given codex id or the first guess
   */
  public String codexIdOrFirstGuess(String codexId) {
    lock.lock();
    try {
      var codex = codexController.getCodexStatus(codexId);
      if (codex.isPresent()) {
        return codexId;
      }
      var firstGuess = codexController.findFirstStartingWith(codexId);
      return firstGuess.isPresent() ? firstGuess.orElseThrow().codex().id() : codexId;
    } finally {
      lock.unlock();
    }
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
            .addMessage(new WhisperMessage(settings.getStr("login"), message, System.currentTimeMillis()));
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

  String getUserOrFirstGuess(String username) {
    var usernameAsked = username;
    if (!users.contains(username)) {
      usernameAsked = users.stream()
              .filter(u -> u.startsWith(username))
              .findFirst().orElse(username);
    }
    return usernameAsked;
  }

  public Optional<DirectMessages> getDirectMessagesOf(String username) {
    lock.lock();
    try {
      var dm = directMessages.values()
              .stream()
              .filter(u -> u.username()
                      .equals(username))
              .findFirst();
      dm.ifPresent(r -> {
        if (!r.hasMessages()) {
          r.addMessage(new WhisperMessage("(info)", STR."This is the beginning of your direct message history with \{username}", System.currentTimeMillis()));
        }
      });
      if (dm.isEmpty() && users.contains(username)) {
        var newDM = DirectMessages.create(username);
        directMessages.put(newDM.id(), newDM);
        return Optional.of(newDM);
      }
      return dm;
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
   *
   * @param id     the id of the codex
   * @param hidden if the codex must be downloaded in hidden mode
   */
  public void download(String id, boolean hidden, int chainSize) {
    lock.lock();
    try {
      codexController.download(id, hidden);
      requestSocketForDownload(id, hidden, chainSize);
    } finally {
      lock.unlock();
    }
  }

  private void requestSocketForDownload(String codexId, boolean hidden, int chainSize) {
    clientContext.queueFrame(new RequestDownload(codexId, (byte) (hidden ? 1 : 0),
            settings.getInt("sharersRequired"),
            chainSize != 0 ? chainSize : settings.getInt("proxyChainSize")));
    codexIdOfAskedDownload.addLast(codexId);
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
      currentDownloads.remove(codexId);
      // clientContext.queueFrame(new Cancel(codexId)); @Todo
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Share a codex
   * changes the status of the codex to shared
   * and send a propose message to the server
   */
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

  public boolean codexExists(String id) {
    return codexController.codexExists(id);
  }

  /**
   * Get a chunk of a codex
   *
   * @param wantedCodexId the id of the codex
   * @param offset        the offset of the chunk
   * @param length        the length of the chunk
   * @return the chunk of data
   * @throws IllegalArgumentException if the codex does not exist
   */
  public byte[] getChunk(String wantedCodexId, long offset, int length) throws IOException {
    lock.lock();
    try {
      if (!codexController.codexExists(wantedCodexId)) {
        throw new IllegalArgumentException("The codex does not exist");
      }
      return codexController.getChunk(wantedCodexId, offset, length);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Remove a user from the chat
   * @param username the username of the user
   */
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
  
  /**
   * Update the presence of users
   * @param usernames the list of users
   */
  public void addUsersFromDiscovery(List<String> usernames) {
    lock.lock();
    try {
      users.addAll(usernames);
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Get the listening port of the client
   */
  public int listeningPort() {
    return connectionManager.listeningPort();
  }
  
  /**
   * Save received sockets for the request of an open download.
   * @param sockets the sockets of the sharers
   */
  public void addSocketsOpenDownload(SocketField[] sockets) {
    sharersSocketQueue.add(new SocketResponse(sockets, null));
  }
  
  /**
   * Save received sockets for the request of a hidden download.
   * Used by the client context
   * @param proxySockets the sockets of proxy nodes
   */
  public void addSocketsHiddenDownload(ProxyNodeSocket[] proxySockets) {
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
   *
   * @param id      the id of the codex
   * @param offset  the offset of the chunk
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
  
  /**
   * Search for codexes with a specific name on the server
   * @param search the search query
   * @return the search response
   */
  public Optional<SearchResponse> searchCodexes(Search search) {
    try {
      searchResponseQueue.clear();
      clientContext.queueFrame(search);
      logger.info(STR."(searchCodexes) searching for codexes with name \{search.codexName()}");
      SearchResponse response = searchResponseQueue.poll(settings.getInt("searchTimeout"), java.util.concurrent.TimeUnit.SECONDS);
      if (response == null) {
        return Optional.empty();
      }
      return Optional.of(response);
    } catch (InterruptedException e) {
      close();
      return Optional.empty();
    }
  }
  
  /**
   * Save the search response in the queue
   * Used by the client context
   * @param response the search response
   */
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
    this.publicMessages.addAll(Arrays.asList(messages));
    //var userId = UUID.randomUUID();
    //this.directMessages.put(userId, new DirectMessages(userId, "Alan1"));
    // test codex
    var login = settings.getStr("login");
    if (login.equals("Alan1") || login.equals("Alan2") || login.equals("Alan3")) {
      //var status = codexController.createFromPath("my codex", "/mnt/d/testReseau2");
      //var status = codexController.createFromPath("test", "/home/alan1/Documents/tmp/tablette");
      //var status = codexController.createFromPath("test", "/home/alan1/Pictures");
      var status = codexController.createFromPath("test", "/home/alan1/Downloads/aaa");
      share(status.id());
      try {
        var status2 = codexController.createFromPath("test2", "/home/alan1/Downloads/u7xn3f.mp4");
        share(status2.id());
      } catch (IOException e) {
        logger.warning(e.getMessage());
      }
      try {
        var status3 = codexController.createFromPath("test2", "/home/alan1/Downloads/bbb");
        share(status3.id());
      } catch (IOException e) {
        logger.warning(e.getMessage());
      }
    }
  }

  /**
   * Create a splash screen logo with a list of messages
   * showing le title "Chadow" in ascii art and the version
   */
  public List<YellMessage> splashLogo() {
    return List.of(
            new YellMessage("", "┏┓┓    ┓", 0),
            new YellMessage("", "┃ ┣┓┏┓┏┫┏┓┓┏┏", 0),
            new YellMessage("", "┗┛┗┗┗┗┗┗┗┛┗┛┛ v1.0.0 - Bastos & Sebbah\n", 0),
            new YellMessage("", "All files shared here are not stored on our server. We cannot be held responsible for any misuse of the platform.", 0),
            new YellMessage("", "Please note that messages are not encrypted. This project has no claim to be anything other than educational.", 0),
            new YellMessage("", "Use at your own risk and comply with laws.\n", 0)
    );
  }
}