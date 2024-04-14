package fr.uge.chadow.client;

import fr.uge.chadow.core.context.Context;

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

public class ClientContextHandler {
  private static final Logger logger = Logger.getLogger(ClientContextHandler.class.getName());
  
  public record ConnectionData(Context context, InetSocketAddress address) {}
  private final Selector selector;
  private final LinkedBlockingQueue<Function<SelectionKey, ConnectionData>> contextQueue = new LinkedBlockingQueue<>();
  private final ServerSocketChannel serverSocketChannel;
  
  public ClientContextHandler() throws IOException {
    this.selector = Selector.open();
    this.serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.socket().getLocalPort();
  }
  
  public void supplyConnectionData(Function<SelectionKey, ConnectionData> connectionDataSupplier) {
    contextQueue.add(connectionDataSupplier);
  }
  
  public void launch() throws IOException {
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
      throw new UncheckedIOException(ioe);
    }
  }
  
  public int getLocalPort() {
    return serverSocketChannel.socket().getLocalPort();
  }
}