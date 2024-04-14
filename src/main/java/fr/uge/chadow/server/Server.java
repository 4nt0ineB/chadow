package fr.uge.chadow.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import fr.uge.chadow.core.context.ClientContext;
import fr.uge.chadow.core.context.ContextHandler;
import fr.uge.chadow.core.context.ServerContext;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.RequestOpenDownload;
import fr.uge.chadow.core.protocol.server.RequestResponse;

public class Server {
  public record SocketInfo(SocketChannel socketChannel, InetSocketAddress address, ServerContext serverContext){
  }

  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private final ContextHandler contextHandler;
  private final Map<String, SocketInfo> clients = new HashMap<>();
  private final Map<Codex, List<String>> codexes = new HashMap<>(); // codex -> list of usernames

  public Server(int port) throws IOException {
    this.contextHandler = new ContextHandler(key -> new ServerContext(this, key), port);
  }

  public void start() throws IOException {
    logger.info("STARTS");
    
    Thread.ofPlatform()
          .start(() -> {
            try {
              logger.info("Client starts");
              contextHandler.launch();
            } catch (IOException e) {
              logger.severe(STR."The client was interrupted. \{e.getMessage()}");
            }
          });
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
    contextHandler.broadcast(frame);
  }

  public void whisper(WhisperMessage message, String username_sender) {
    var serverContext = getServerContext(message.username());
    var newMessage = new WhisperMessage(username_sender, message.txt(), System.currentTimeMillis());
    serverContext.queueFrame(newMessage);
    logger.info(STR."Whispering message \{message.txt()} to \{message.username()}");
  }

  public void propose(Codex codex, String username) {
    var clientCodexes = codexes.computeIfAbsent(codex, k -> new ArrayList<>());
    logger.info(STR."Proposing: \{codex}");
    clientCodexes.add(username);
  }

  public void request(String codexId, ServerContext serverContext) {
    logger.info(STR."map : \{codexes}");
    var codex = codexes.keySet().stream().filter(c -> c.id().equals(codexId)).findFirst().orElse(null);
    if (codex == null) {
      logger.warning(STR."Codex \{codexId} not found");
      return;
    }
    serverContext.queueFrame(new RequestResponse(codex));
  }

  public void requestOpenDownload(String codexId, ServerContext serverContext) {
    var sharersList = codexes.entrySet().stream()
            .filter(e -> e.getKey().id().equals(codexId))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow();

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