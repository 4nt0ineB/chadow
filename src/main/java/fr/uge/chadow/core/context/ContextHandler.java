package fr.uge.chadow.core.context;

import fr.uge.chadow.core.protocol.Frame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.logging.Logger;

public class ContextHandler {
  private static final Logger logger = Logger.getLogger(ContextHandler.class.getName());
  
  public record ConnectionData(Context context, InetSocketAddress address) {}
  private final Selector selector;
  private final LinkedBlockingQueue<Function<SelectionKey, ConnectionData>> contextQueue = new LinkedBlockingQueue<>();
  private final ServerSocketChannel serverSocketChannel;
  private final Function<SelectionKey, Context> sharerContextFactory;
  
  public ContextHandler(Function<SelectionKey, Context> sharerContextFactory, int serverPort) throws IOException {
    this.selector = Selector.open();
    this.serverSocketChannel = ServerSocketChannel.open();
    this.serverSocketChannel.bind(new InetSocketAddress(serverPort));
    logger.info(STR."Port opened: \{serverSocketChannel.socket().getLocalPort()}");
    this.sharerContextFactory = sharerContextFactory;
  }
  
  public void supplyConnectionData(Function<SelectionKey, ConnectionData> connectionDataSupplier) {
    contextQueue.add(connectionDataSupplier);
    selector.wakeup();
  }
  
  public void launch() throws IOException {
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    while (!Thread.interrupted()) {
      try {
        processContexts();
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }
  
  private void processContexts() throws IOException {
    while (!contextQueue.isEmpty()) {
      var connectionDataSupplier = contextQueue.poll();
      if (connectionDataSupplier != null) {
        var sc = SocketChannel.open();
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        var connectionData = connectionDataSupplier.apply(key);
        key.attach(connectionData.context());
        sc.connect(connectionData.address());
      }
    }
  }
  
  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isAcceptable()) {
        doAccept(key);
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
    try {
      if (key.isValid() && key.isConnectable()) {
        ((Context) key.attachment()).doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        ((Context) key.attachment()).doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        ((Context) key.attachment()).doRead();
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      // throw new UncheckedIOException(ioe);
      logger.info("Connection closed with client due to IOException");
      ((Context) key.attachment()).silentlyClose();
    }
  }
  
  private void doAccept(SelectionKey key) throws IOException {
    var sc = serverSocketChannel.accept();
    if (sc == null) {
      logger.warning("selector gave wrong hint for accept");
      return;
    }
    sc.configureBlocking(false);
    logger.info(STR."Connection accepted from: \{sc.getRemoteAddress()}");
    var sckey = sc.register(selector, SelectionKey.OP_READ);
    sckey.attach(sharerContextFactory.apply(sckey));
  }
  
  public int listeningPort() {
    return serverSocketChannel.socket().getLocalPort();
  }
  
  /**
   * Broadcast a frame to all contexts
   *
   * @param frame the frame to broadcast
   */
  public void broadcast(Frame frame) {
    for (var key : selector.keys()) {
      var session = ((Context) key.attachment());
      if (session != null) {
        session.queueFrame(frame);
        logger.info(STR."Broadcasting frame \{frame}");
      }
    }
  }
  
}