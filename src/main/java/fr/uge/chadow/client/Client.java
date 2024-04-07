package fr.uge.chadow.client;

import fr.uge.chadow.core.context.ClientContext;
import fr.uge.chadow.core.context.Context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class Client {
  
  private final SocketChannel sc;
  private final Selector selector;
  private final InetSocketAddress serverAddress;
  private final ClientAPI api;
  private Context clientContext;
  
  public Client(InetSocketAddress serverAddress, ClientAPI api) throws IOException {
    Objects.requireNonNull(serverAddress);
    Objects.requireNonNull(api);
    this.serverAddress = serverAddress;
    this.sc = SocketChannel.open();
    this.selector = Selector.open();
    this.api = api;
  }
  
  public void launch() throws IOException {
    sc.configureBlocking(false);
    var key = sc.register(selector, SelectionKey.OP_CONNECT);
    clientContext = new ClientContext(key, api);
    key.attach(clientContext);
    sc.connect(serverAddress);
    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }
  
  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isConnectable()) {
        clientContext.doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        clientContext.doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        clientContext.doRead();
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
  }
}