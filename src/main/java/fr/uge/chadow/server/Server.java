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
import fr.uge.chadow.core.protocol.client.Search;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.*;

public class Server {
  public record SocketInfo(SocketChannel socketChannel, InetSocketAddress address, ServerContext serverContext){
  }
  
  public record CodexRecord(Codex codex, long registrationDate) {
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
            .limit(numberOfSharers)
            .findFirst()
            .orElseThrow();
    // TODO: add a random selection of sharers

    var sharersSocketFieldArray = sharersList.stream()
            .map(clients::get)
            .map(SocketInfo::address)
            .map(address -> new SocketField(address.getAddress().getAddress(), address.getPort()))
            .toArray(SocketField[]::new);

    serverContext.queueFrame(new RequestOpenDownload(sharersSocketFieldArray));
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
        .filter(c ->  c.codex().name().contains(search.codexName()))
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