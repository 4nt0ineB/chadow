package fr.uge.chadow.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.YellMessage;

public class Server {
  private static final Logger logger = Logger.getLogger(Server.class.getName());

  private final ServerSocketChannel serverSocketChannel;
  private final Selector selector;
  private final Map<String, SocketChannel> clients = new HashMap<>();

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
        ((Session) key.attachment()).doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        ((Session) key.attachment()).doRead();
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
    sckey.attach(new Session(this, sckey));
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
   * Add a message to all connected clients queue
   *
   * @param msg the message to broadcast
   */
  void broadcast(YellMessage msg) {
    for (var key : selector.keys()) {
      var session = ((Session) key.attachment());
      if (session != null) {
        session.queueFrame(msg);
        logger.info(STR."Broadcasting message \{msg.txt()}");
      }
    }
  }

  public void whisper(WhisperMessage message, String username_sender) {
    var sc = clients.get(message.username());
    if (sc == null) {
      logger.warning(STR."Client \{message.username()} not found");
      return;
    }
    var session = (Session) sc.keyFor(selector).attachment();
    var newMessage = new WhisperMessage(username_sender, message.txt(), System.currentTimeMillis());
    session.queueFrame(newMessage);
    logger.info(STR."Whispering message \{message.txt()} to \{message.username()}");
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