package fr.uge.chadow.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chadow.core.context.ServerContext;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse.Username;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.RequestResponse;

public class Server {
  private static final Logger logger = Logger.getLogger(Server.class.getName());

  private final ServerSocketChannel serverSocketChannel;
  private final Selector selector;
  private final Map<String, SocketChannel> clients = new HashMap<>();
  private final Map<Codex, List<String>> codexes = new HashMap<>(); // codex -> list of usernames

  public Server(int port) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    selector = Selector.open();
  }

  public void launch() throws IOException {
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    while (!Thread.interrupted()) {
      //Helpers.printKeys(selector); // for debug
      System.out.println("Starting select");
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
      System.out.println("Select finished");
    }
  }

  private void treatKey(SelectionKey key) {
    // Helpers.printSelectedKey(key); // for debug
    try {
      if (key.isValid() && key.isAcceptable()) {
        doAccept(key);
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
    try {
      if (key.isValid() && key.isWritable()) {
        ((ServerContext) key.attachment()).doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        ((ServerContext) key.attachment()).doRead();
      }
    } catch (IOException e) {
      logger.log(Level.INFO, "Connection closed with client due to IOException", e);
      silentlyClose(key);
    }
  }

  private void doAccept(SelectionKey key) throws IOException {
    var sc = serverSocketChannel.accept();
    if (sc == null) {
      logger.warning("selector gave wrong hint for accept");
      return;
    }
    sc.configureBlocking(false);
    var sckey = sc.register(selector, SelectionKey.OP_READ);
    sckey.attach(new ServerContext(this, sckey));
  }

  private void silentlyClose(SelectionKey key) {
    Channel sc = (Channel) key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }

  /**
   * Get the server context associated with the given username
   *
   * @param username the username to get the server context from
   * @return the server context associated with the given username
   */
  private ServerContext getServerContext(String username) {
    var sc = clients.get(username);
    return (ServerContext) sc.keyFor(selector).attachment();
  }

  public void discovery(ServerContext serverContext) {
    var username = serverContext.login();
    var usernames = clients.keySet().stream()
            .filter(client -> !client.equals(username))
            .map(Username::new)
            .toArray(Username[]::new);
    serverContext.queueFrame(new DiscoveryResponse(usernames));
  }

  /**
   * Broadcast a frame to all connected clients
   *
   * @param frame the frame to broadcast
   */
  public void broadcast(Frame frame) {
    for (var key : selector.keys()) {
      var session = ((ServerContext) key.attachment());
      if (session != null) {
        session.queueFrame(frame);
        logger.info(STR."Broadcasting frame \{frame}");
      }
    }
  }

  public void whisper(WhisperMessage message, String username_sender) {
    var serverContext = getServerContext(message.username());
    var newMessage = new WhisperMessage(username_sender, message.txt(), System.currentTimeMillis());
    serverContext.queueFrame(newMessage);
    logger.info(STR."Whispering message \{message.txt()} to \{message.username()}");
  }

  public void propose(Codex codex, String username) {
    var clientCodexes = codexes.computeIfAbsent(codex, k -> new ArrayList<>());
    logger.info("Proposing: " + codex);
    clientCodexes.add(username);
  }

  public void request(String codexId, ServerContext serverContext) {
    logger.info("map : " + codexes);
    var codex = codexes.keySet().stream()
            .filter(c -> c.id().equals(codexId))
            .findFirst().orElse(null);
    if (codex == null) {
      logger.warning(STR."Codex \{codexId} not found");
      return;
    }
    serverContext.queueFrame(new RequestResponse(codex));
  }

  public boolean addClient(String login, SocketChannel sc) {
    if (clients.containsKey(login)) {
      return false;
    }
    clients.put(login, sc);
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
      new Server(Integer.parseInt(args[0])).launch();
    } catch (IOException e) {
      logger.severe(STR."Error while launching server: \{e.getMessage()}");
    }
  }

  private static void usage() {
    System.out.println("Usage : ServerSumBetter port");
  }
}