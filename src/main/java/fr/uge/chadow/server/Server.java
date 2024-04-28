package fr.uge.chadow.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

import fr.uge.chadow.core.TCPConnectionManager;
import fr.uge.chadow.core.context.ServerContext;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.client.RequestDownload;
import fr.uge.chadow.core.protocol.client.Search;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.*;

public class Server {
  public record SocketInfo(SocketChannel socketChannel, InetSocketAddress address, ServerContext serverContext) {
  }

  public record CodexRecord(Codex codex, long registrationDate) {
  }

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
     * @param chains          A map associating the chain ID to a list of usernames.
     *                        The list of usernames represents the proxies in the chain.
     */
    private record ProxiesDetails(int numberOfProxies, int numberOfSharers, Map<Integer, List<String>> chains) {
    }

    // Map to store client requests and associated proxy details.
    private final Map<ClientRequest, ProxiesDetails> requests = new HashMap<>();

    // Map to store the client request associated with a chain ID.
    private final Map<String, ClientRequest> chainIdToRequest = new HashMap<>();

    // Map to store the scores of proxies.
    // The key is the username of the proxy and the value is the score.
    // The score is used to determine the best proxy to use.
    private final Map<String, Integer> proxyScores = new HashMap<>();

    public void initRequest(RequestDownload requestDownload, ServerContext serverContext) {
      var possibleSharers = calculatePossibleSharers(requestDownload.numberOfProxies(), requestDownload.numberOfSharers(), requestDownload.codexId());
      if (possibleSharers == -1) {
        // TODO : send an error message
        // serverContext.queueFrame(new Error("Not enough proxies available"));
        return;
      }

      var clientRequest = new ClientRequest(serverContext, requestDownload.codexId());
      var proxiesDetails = new ProxiesDetails(requestDownload.numberOfProxies(), possibleSharers, new HashMap<>());


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
  }


  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private final Map<String, SocketInfo> clients = new HashMap<>();
  private final Map<CodexRecord, List<String>> codexes = new HashMap<>(); // codex -> list of usernames
  private TCPConnectionManager connectionManager;
  private final int port;

  public Server(int port) throws IOException {
    this.port = port;
  }

  public void start() throws IOException {
    this.connectionManager = new TCPConnectionManager(port, key -> new ServerContext(this, key));
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
    var clientCodexes = codexes.computeIfAbsent(new CodexRecord(codex, System.currentTimeMillis()), k -> new ArrayList<>());
    logger.info(STR."Proposing: \{codex}");
    clientCodexes.add(username);
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

  public void requestClosedDownload(ServerContext serverContext, String codexId, int numberOfSharers, int numberOfProxies) {
    var sharersList = codexes.entrySet().stream()
            .filter(e -> e.getKey().codex().id().equals(codexId))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow();


  }

  public boolean addClient(String login, SocketChannel sc, InetSocketAddress address, ServerContext serverContext) {
    if (clients.containsKey(login)) {
      return false;
    }
    clients.put(login, new SocketInfo(sc, address, serverContext));
    return true;
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
              return new SearchResponse.Result(codex.name(), codex.id(), codexRegistration.registrationDate, codexes.get(codexRegistration).size());
            })
            .toArray(SearchResponse.Result[]::new);
    return new SearchResponse(filteredCodexes);
  }

  public void removeClient(String login) {
    logger.info(STR."Client \{login} has disconnected");
    clients.remove(login);
    broadcast(new Event((byte) 0, login));
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      usage();
      return;
    }
    try {
      new Server(Integer.parseInt(args[0])).start();
    } catch (IOException e) {
      logger.severe(STR."Error while launching server: \{e.getMessage()}");
    }
  }

  private static void usage() {
    System.out.println("Usage : ServerSumBetter port");
  }


}