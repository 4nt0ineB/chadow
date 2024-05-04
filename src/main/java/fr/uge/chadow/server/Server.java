package fr.uge.chadow.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

import fr.uge.chadow.Settings;
import fr.uge.chadow.core.ProxyManager;
import fr.uge.chadow.core.TCPConnectionManager;
import fr.uge.chadow.core.context.*;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.client.RequestDownload;
import fr.uge.chadow.core.protocol.client.Search;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.ProxyNodeSocket;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.*;

public class Server {
  /**
   * Class to manage client requests and associated proxy details.
   */
  private class ProxyHandler {
    /**
     * Represents a client request.
     *
     * @param serverContext The server context associated with the request.
     * @param codexId       The ID of the codex requested by the client.
     */
    private record ClientRequest(ServerContext serverContext, String codexId) {
    }

    /**
     * Represents the details of proxies associated with a client request.
     *
     * @param numberOfProxies The number of proxies associated with the request.
     * @param numberOfSharers The number of sharers associated with the request.
     * @param chains          A map associating the chain ID to the details of the chain.
     *                        The list of usernames represents the proxies in the chain.
     */
    private record ProxiesDetails(int numberOfProxies, int numberOfSharers, Map<Integer, ChainDetails> chains) {
    }

    /**
     * Represents the details of a chain.
     *
     * @param proxiesContacted A list of usernames representing the proxies contacted in the chain.
     * @param proxiesConfirmed A set of usernames representing the proxies that have confirmed the chain.
     */
    private record ChainDetails(List<String> proxiesContacted, Set<String> proxiesConfirmed) {
    }

    // Map to store client requests and associated proxy details.
    private final Map<ClientRequest, ProxiesDetails> requests = new HashMap<>();

    // Map to store the client request associated with a chain ID.
    private final Map<Integer, ClientRequest> chainIdToRequest = new HashMap<>();

    // Map to store the scores of proxies.
    // The key is the username of the proxy and the value is the score.
    // The score is used to determine the best proxy to use.
    private final Map<String, Integer> proxyScores = new HashMap<>();
    private final Random random = new Random();

    public void initRequest(RequestDownload requestDownload, ServerContext serverContext) {
      var possibleSharers = calculatePossibleSharers(requestDownload.numberOfProxies(),
              requestDownload.numberOfSharers(), requestDownload.codexId());
      logger.info(STR."Possible sharers: \{possibleSharers}");

      if (possibleSharers <= 0) {
        // TODO : send an error message
        // serverContext.queueFrame(new Error("Not enough proxies available"));
        return;
      }

      var sharersList = codexes.entrySet().stream()
              .filter(e -> e.getKey().codex().id().equals(requestDownload.codexId()))
              .map(Map.Entry::getValue)
              .limit(possibleSharers)
              .findFirst()
              .orElseThrow();

      var clientRequest = new ClientRequest(serverContext, requestDownload.codexId());
      var proxiesDetails = new ProxiesDetails(requestDownload.numberOfProxies(), possibleSharers, new HashMap<>());

      // Add the client request and associated proxies details to the requests map
      requests.put(clientRequest, proxiesDetails);

      // Create the different chainIds and add them to the proxiesDetails
      for (int i = 0; i < possibleSharers; i++) {
        var newChainId = generateUniqueInt(chainIdToRequest);
        logger.info(STR."New chainId: \{newChainId}");
        proxiesDetails.chains.put(newChainId, new ChainDetails(new ArrayList<>(), new HashSet<>()));
        chainIdToRequest.put(newChainId, clientRequest);

        // Add the Sharer to the contacted proxies
        proxiesDetails.chains.get(newChainId).proxiesContacted.add(sharersList.get(i));
      }

      // Contact the proxies for each chain
      for (var chainId : proxiesDetails.chains.keySet()) {
        logger.info(STR."Contacting proxies for chain \{chainId}");
        contactingProxiesForAChain(chainId, requestDownload.numberOfProxies(), sharersList, serverContext.login(),
                proxiesDetails);
      }
    }

    /**
     * Initiates the process of contacting proxies for a given chain, starting from the last proxy in the chain
     * and working towards the first proxy.
     *
     * @param chainId         The ID of the chain for which proxies are being contacted.
     * @param numberOfProxies The number of proxies to contact for the chain.
     * @param sharers         The list of sharers associated with the requested resource.
     * @param client          The client initiating the request.
     * @param proxiesDetails  Details about the proxies and chains involved in the process.
     */
    public void contactingProxiesForAChain(int chainId, int numberOfProxies, List<String> sharers, String client,
                                           ProxiesDetails proxiesDetails) {
      Set<String> contactedProxies = new HashSet<>();
      Set<String> sharersSet = new HashSet<>(sharers);

      // The current proxyContacted list contains only one element, the sharer to contact
      String currentProxyToContact = proxiesDetails.chains.get(chainId).proxiesContacted.getFirst();
      logger.info(STR."The sharer to contact: \{currentProxyToContact} in the chain \{chainId}");


      for (int i = 0; i < numberOfProxies; i++) {
        String proxyUsername = selectBestProxy(sharersSet, contactedProxies, client);
        logger.info(STR."Next proxy in the chain: \{proxyUsername}");

        contactedProxies.add(proxyUsername);
        proxyScores.put(proxyUsername, proxyScores.getOrDefault(proxyUsername, 0) + 1);

        proxiesDetails.chains.get(chainId).proxiesContacted.add(proxyUsername);

        SocketField proxySocket = new SocketField(clients.get(currentProxyToContact).address().getAddress()
                .getAddress(), clients.get(currentProxyToContact).address().getPort());

        // Send a Proxy Frame to the proxy
        clients.get(proxyUsername).serverContext.queueFrame(new Proxy(chainId, proxySocket));
        logger.info(STR."Sending Proxy frame to \{proxyUsername}");

        currentProxyToContact = proxyUsername;
      }
    }

    /**
     * Selects the best proxy from the available proxies based on certain criteria.
     *
     * @param sharers          The set of sharers associated with the requested resource.
     * @param contactedProxies The set of proxies that have already been contacted.
     * @param client           The client making the request.
     * @return The best proxy to contact based on the given criteria.
     * @throws NoSuchElementException if no proxy can be selected.
     */
    private String selectBestProxy(Set<String> sharers, Set<String> contactedProxies, String client) {
      return proxyScores.entrySet().stream()
              .filter(e -> !client.equals(e.getKey()))
              .filter(e -> !sharers.contains(e.getKey()))
              .filter(e -> !contactedProxies.contains(e.getKey()))
              .min(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElseThrow();
    }

    /**
     * Generates a unique integer that is not already present in the given map.
     *
     * @param map The map to check for the presence of the generated integer.
     * @return A unique integer that is not already present in the given map.
     */
    private int generateUniqueInt(Map<Integer, ?> map) {
      int uniqueInt;
      do {
        uniqueInt = random.nextInt();
      } while (map.containsKey(uniqueInt));
      return uniqueInt;
    }

    /**
     * Calculates the possible number of sharers for a given codex based on the number of proxies available
     * and the number of sharers requested.
     *
     * @param numberOfProxies The total number of proxies available.
     * @param numberOfSharers The number of sharers requested.
     * @param codexId         The ID of the codex for which to calculate the possible number of sharers.
     * @return The possible number of sharers if the requested number of proxies is reasonable, otherwise -1.
     */
    public int calculatePossibleSharers(int numberOfProxies, int numberOfSharers, String codexId) {
      var possibleSharers = codexes.entrySet().stream()
              .filter(e -> e.getKey().codex().id().equals(codexId))
              .mapToInt(e -> e.getValue().size())
              .sum();
      possibleSharers = Math.min(possibleSharers, numberOfSharers);

      var possibleProxies = clients.size() - 1 - possibleSharers;

      while (possibleProxies < numberOfProxies) {
        if (possibleSharers == 0) {
          return -1;
        }
        possibleSharers--;
        possibleProxies++;
      }

      return possibleSharers;
    }

    /**
     * Handles the confirmation of a proxy for a specific chain and manages the completion status of the associated request.
     * If all proxies in the chain have confirmed the request, sends a response to the client.
     *
     * @param serverContext The server context of the confirming proxy.
     * @param chainId       The ID of the chain for which the proxy confirms the request.
     */
    public void proxyConfirmed(ServerContext serverContext, int chainId) {
      // Retrieve the client request associated with the chain ID
      var clientRequest = chainIdToRequest.get(chainId);

      // Retrieve the details of proxies associated with the client request
      var proxiesDetails = requests.get(clientRequest);

      // Add the confirming proxy to the list of confirmed proxies for the chain
      var chainDetails = proxiesDetails.chains.get(chainId);
      chainDetails.proxiesConfirmed.add(serverContext.login());

      // Manage the completion status of the associated request
      manageRequest(clientRequest);
    }


    /**
     * Manages the completion status of a client request and sends a response if the request is complete.
     *
     * @param clientRequest The client request to manage.
     */
    private void manageRequest(ClientRequest clientRequest) {
      // Retrieve the details of proxies associated with the client request
      var proxiesDetails = requests.get(clientRequest);

      // Check if all proxies in each chain have confirmed the request
      for (var chainDetails : proxiesDetails.chains.values()) {
        if (chainDetails.proxiesConfirmed.size() != proxiesDetails.numberOfProxies) {
          // If the request is not complete, log the information and return
          logger.info(STR."Request of \{clientRequest.codexId()} by \{clientRequest.serverContext.login()} is not complete");
          return;
        }
      }

      // If the request is complete, prepare and send the response
      var proxyNodeSocketArray = proxiesDetails.chains.entrySet().stream()
              .map(entry -> {
                var chainId = entry.getKey();
                var chainDetails = entry.getValue();
                var firstProxyOfChain = chainDetails.proxiesContacted.getLast();

                // Prepare the socket field for the first proxy in the chain
                var socketFieldOfFirstProxy = new SocketField(
                        clients.get(firstProxyOfChain).address().getAddress().getAddress(),
                        clients.get(firstProxyOfChain).address().getPort());

                return new ProxyNodeSocket(socketFieldOfFirstProxy, chainId);
              })
              .toArray(ProxyNodeSocket[]::new);

      logger.info(STR."Request of \{clientRequest.codexId()} by \{clientRequest.serverContext.login()} is complete");
      logger.info(STR."Proxies :\{Arrays.toString(proxyNodeSocketArray)}");

      // Send a response containing the proxy node sockets to the client
      clientRequest.serverContext.queueFrame(new ClosedDownloadResponse(proxyNodeSocketArray));
    }


    /**
     * Removes all instances of a client from the proxy scores and requests, including
     * client requests associated with any chains involving the client.
     *
     * @param username The username of the client to remove.
     */
    public void removeAllInstancesOfClient(String username) {
      // Remove the client from the proxy scores
      proxyScores.remove(username);

      // Remove the requests associated with the client
      requests.entrySet().removeIf(entry -> entry.getKey().serverContext.login().equals(username));

      // Remove the client from the requests where the client is present in any chain
      List<ClientRequest> requestsToRemove = new ArrayList<>();

      for (var entry : requests.entrySet()) {
        var clientRequest = entry.getKey();
        var proxiesDetails = entry.getValue();

        for (var chainDetails : proxiesDetails.chains.values()) {
          if (chainDetails.proxiesContacted.contains(username)) {
            requestsToRemove.add(clientRequest);
            break;
          }
        }
      }

      requestsToRemove.forEach(requests::remove);
    }

    /**
     * Saves the server as a proxy for a given chain ID, sharer, and codex ID.
     * This method is called when the server needs to act as a proxy for a specific client and codex.
     *
     * @param chainId       The ID of the chain for which the server is acting as a proxy.
     * @param serverContext The server context associated with the server acting as a proxy.
     * @param sharer        The username of the sharer involved in the chain.
     * @param codexId       The ID of the codex for which the server is acting as a proxy.
     */
    private void saveServerAsProxy(int chainId, ServerContext serverContext, String sharer, String codexId) {
      var clientRequest = new ClientRequest(serverContext, codexId);
      // Create proxy details for the server acting as a proxy
      var proxiesDetails = new ProxiesDetails(1, 1, new HashMap<>());
      var chainDetails = new ChainDetails(new ArrayList<>(List.of("server", sharer)), new HashSet<>(List.of("server")));

      proxiesDetails.chains.put(chainId, chainDetails);
      requests.put(clientRequest, proxiesDetails);
      chainIdToRequest.put(chainId, clientRequest);
    }

    /**
     * Removes all requests and associated chain IDs for a given codex ID.
     * This method is called when a client has finished downloading a codex.
     *
     * @param codexId The ID of the codex for which to remove requests and associated chain IDs.
     */
    public void removeCodexId(String codexId) {
      // Remove all chain IDs associated with the codex ID
      chainIdToRequest.entrySet().removeIf(entry -> entry.getValue().codexId().equals(codexId));

      // Remove all requests associated with the codex ID
      requests.entrySet().removeIf(entry -> entry.getKey().codexId().equals(codexId));
    }
  }

  /**
   * Represents information about a socket, including the associated socket channel,
   * address, and server context.
   *
   * @param socketChannel The socket channel associated with the socket.
   * @param address       The listening address of the socket.
   * @param serverContext The server context associated with the socket.
   */
  public record SocketInfo(SocketChannel socketChannel, InetSocketAddress address, ServerContext serverContext) {
  }

  /**
   * Represents a record containing information about a codex and its registration date.
   *
   * @param codex            The codex information.
   * @param registrationDate The registration date of the codex.
   */
  public record CodexRecord(Codex codex, long registrationDate) {
  }


  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private final Map<String, SocketInfo> clients = new HashMap<>();
  private final Map<CodexRecord, List<String>> codexes = new HashMap<>(); // codex -> list of usernames
  private TCPConnectionManager connectionManager;

  // Proxy part

  /**
   * Represents the details of the server acting as a proxy in a closed download request.
   * This record encapsulates information such as the codex ID, chain ID, client, and sharer involved.
   *
   * @param codexId The ID of the codex requested in the closed download.
   * @param chainId The ID of the chain for which the server is acting as a proxy.
   * @param client  The username of the client initiating the closed download request.
   * @param sharer  The username of the sharer providing the requested codex.
   */
  private record ServerProxyDetails(String codexId, int chainId, String client, String sharer) {
  }

  private final ProxyHandler proxyHandler = new ProxyHandler(); // handle the different proxies routes
  private final ProxyManager proxyManager = new ProxyManager(); // when server is a
  private ServerProxyDetails serverProxyDetails;
  private final Settings settings;

  public Server(Settings settings) {
    this.settings = settings;
  }

  public void start() throws IOException {
    this.connectionManager = new TCPConnectionManager(settings.getInt("port"), key -> new ServerContext(this, key, settings));
    connectionManager.launch();
  }

  /**
   * Get the server context associated with the given username
   *
   * @param username the username to get the server context from
   * @return the server context associated with the given username
   */
  private ServerContext getServerContext(String username) {
    return clients.get(username).serverContext;
  }

  public void discovery(ServerContext serverContext) {
    var username = serverContext.login();
    var usernames = clients.keySet().stream().filter(client -> !client.equals(username)).toArray(String[]::new);
    serverContext.queueFrame(new DiscoveryResponse(usernames));
  }

  public void broadcast(Frame frame) {
    connectionManager.broadcast(frame);
  }

  public void whisper(WhisperMessage message, String username_sender) {
    var serverContext = getServerContext(message.username());
    var newMessage = new WhisperMessage(username_sender, message.txt(), System.currentTimeMillis());
    serverContext.queueFrame(newMessage);
    logger.info(STR."Whispering message \{message.txt()} to \{message.username()}");
  }

  public void propose(Codex codex, String username) {
    codexes.entrySet().stream()
            .filter(e -> e.getKey().codex().id().equals(codex.id()))
            .map(Map.Entry::getValue)
            .findFirst()
            .ifPresentOrElse(
                    clientCodexes -> clientCodexes.add(username),
                    () -> {
                      var newCodexRecord = new CodexRecord(codex, System.currentTimeMillis());
                      codexes.put(newCodexRecord, new ArrayList<>(List.of(username)));
                    }
            );
  }

  public void request(String codexId, ServerContext serverContext) {
    //logger.info(STR."map : \{codexes}");
    var codex = codexes.keySet().stream()
            .map(CodexRecord::codex)
            .filter(c -> c.id().equals(codexId))
            .findFirst()
            .orElse(null);
    if (codex == null) {
      logger.warning(STR."Codex \{codexId} not found");
      return;
    }
    serverContext.queueFrame(new RequestResponse(codex));
  }

  public SearchResponse search(Search search) {
    Predicate<CodexRecord> dateFilter = c -> {
      if (search.options() == 0) {
        return true;
      }
      var result = false;
      if ((search.options() & Search.Option.AT_DATE.value()) != 0) {
        result |= c.registrationDate == search.date();
      }
      if ((search.options() & Search.Option.BEFORE_DATE.value()) != 0) {
        result |= c.registrationDate < search.date();
      }
      if ((search.options() & Search.Option.AFTER_DATE.value()) != 0) {
        result |= c.registrationDate > search.date();
      }
      return result;
    };

    logger.info(STR."Searching for \{search.codexName()}");
    var filteredCodexes = codexes.keySet().stream()
            .filter(dateFilter)
            .filter(c -> c.codex().name().contains(search.codexName()))
            .skip(search.offset())
            .limit(search.results())
            .map(codexRegistration -> {
              var codex = codexRegistration.codex();
              return new SearchResponse.Result(codex.name(), codex.id(), codexRegistration.registrationDate,
                      codexes.get(codexRegistration).size());
            })
            .toArray(SearchResponse.Result[]::new);
    return new SearchResponse(filteredCodexes);
  }

  public void requestOpenDownload(ServerContext serverContext, String codexId, int numberOfSharers) {
    var sharersList = codexes.entrySet().stream()
            .filter(e -> e.getKey().codex().id().equals(codexId))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow();

    // TODO: add a random selection of sharers
    var sharersSocketFieldArray = sharersList.stream()
            .map(clients::get)
            .map(SocketInfo::address)
            .map(address -> new SocketField(address.getAddress().getAddress(), address.getPort()))
            .limit(numberOfSharers)
            .toArray(SocketField[]::new);

    serverContext.queueFrame(new RequestOpenDownload(sharersSocketFieldArray));
  }

  // -------------------------------- Proxy part / Closed download --------------------------------

  /**
   * Handles a request for a closed download initiated by a client.
   * If the number of proxies requested is 1 and there are only 2 clients connected to the server,
   * the server acts as a proxy. Otherwise, the request is initialized by the ProxyHandler.
   *
   * @param serverContext   The server context associated with the request.
   * @param requestDownload The request for a closed download initiated by the client.
   */
  public void requestClosedDownload(ServerContext serverContext, RequestDownload requestDownload) {
    if (requestDownload.numberOfProxies() == 1 && clients.size() == 2) {
      // The Server is used as a proxy
      // Save the proxy route
      var chainId = proxyHandler.generateUniqueInt(proxyHandler.chainIdToRequest);
      saveServerProxyRoute(chainId, requestDownload, serverContext);

      // Send the server as a proxy to the client requesting the download
      sendServerAsProxy(serverContext, chainId);
    } else {
      // The Server is not used as a proxy
      // Initialize the request
      proxyHandler.initRequest(requestDownload, serverContext);
    }
  }

  /**
   * Saves the server as a proxy for a given chain ID, request download, and server context.
   * This method is called when the server needs to act as a proxy for a specific client and request download.
   *
   * @param chainId         The ID of the chain for which the server is acting as a proxy.
   * @param requestDownload The request for a closed download for which the server is acting as a proxy.
   * @param serverContext   The server context associated with the server acting as a proxy.
   */
  private void saveServerProxyRoute(int chainId, RequestDownload requestDownload, ServerContext serverContext) {
    var sharerName = codexes.entrySet().stream()
            .filter(e -> e.getKey().codex().id().equals(requestDownload.codexId()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow()
            .getFirst();
    var sharerSocket = new SocketField(clients.get(sharerName).address().getAddress().getAddress(),
            clients.get(sharerName).address().getPort());

    serverProxyDetails = new ServerProxyDetails(requestDownload.codexId(), chainId, serverContext.login(), sharerName);


    // Save the proxy route for the server.
    // This should be the initial route established for the chain.
    proxyManager.saveProxyRoute(chainId, sharerSocket);

    // Save the proxy route for the ProxyHandler.
    // This is done to handle potential future requests for closed downloads from other clients.
    proxyHandler.saveServerAsProxy(chainId, serverContext, sharerName, requestDownload.codexId());
  }

  /**
   * Sends the server information as a proxy to the client for a given chain ID.
   *
   * @param serverContext The server context associated with the server.
   * @param chainId       The ID of the chain for which the server is acting as a proxy.
   */
  private void sendServerAsProxy(ServerContext serverContext, int chainId) {
    // Create a ProxyNodeSocket for the server acting as a proxy
    var serverProxyNodeSocket = new ProxyNodeSocket(serverContext.getServerPublicAddress(), chainId);

    // Send a ClosedDownloadResponse containing the server as a proxy to the client
    serverContext.queueFrame(new ClosedDownloadResponse(new ProxyNodeSocket[]{serverProxyNodeSocket}));
  }

  public void proxyOk(ServerContext serverContext, int chainId) {
    logger.info(STR."Proxy \{serverContext.login()} confirmed chain \{chainId}");
    if (serverProxyDetails != null && serverProxyDetails.chainId() == chainId) {
      logger.info(STR."Server is acting as a proxy for chain \{chainId}");
    } else {
      proxyHandler.proxyConfirmed(serverContext, chainId);
    }
  }

  public void update(String codexId, String client) {
    if (serverProxyDetails != null && serverProxyDetails.codexId().equals(codexId) && serverProxyDetails.client().equals(client)) {
      logger.info(STR."Server is no more a proxy for \{codexId}");
      serverProxyDetails = null;
    }
    proxyHandler.removeCodexId(codexId);
  }

  public boolean setUpBridge(int chainId, ProxyBridgeLeftSideContext clientAsServerContext) {
    var socket = proxyManager.getNextHopSocket(chainId);
    if (socket.isEmpty()) {
      return false;
    }
    connectionManager.addContext(socket.orElseThrow(), key -> new ProxyBridgeRightSideContext(key, clientAsServerContext));
    return true;
  }

  // --------------------------------------------------------------------------------------------------

  public boolean addClient(String login, SocketChannel sc, InetSocketAddress address, ServerContext serverContext) {
    if (clients.containsKey(login)) {
      logger.warning(STR."Login \{login} already in use");
      return false;
    }
    clients.put(login, new SocketInfo(sc, address, serverContext));
    proxyHandler.proxyScores.put(login, 0);
    return true;
  }

  public void removeClient(String login) {
    logger.info(STR."Client \{login} has disconnected");
    clients.remove(login);
    proxyHandler.removeAllInstancesOfClient(login);
    broadcast(new Event((byte) 0, login));
  }
}