package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.Message;
import fr.uge.chadow.core.reader.MessageReader;
import fr.uge.chadow.core.reader.Reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.List;

public class Client {
  
  private static final int BUFFER_SIZE = 10_000;
  private static final Logger logger = Logger.getLogger(Client.class.getName());
  private final SocketChannel sc;
  private final Selector selector;
  private final InetSocketAddress serverAddress;
  private final String login;
  private final ArrayBlockingQueue<String> commandsQueue = new ArrayBlockingQueue<>(1);
  private Context uniqueContext;
  
  private final LinkedBlockingQueue<Message> receivedMessages = new LinkedBlockingQueue<>();
  
  public Client(String login, InetSocketAddress serverAddress) throws IOException {
    this.serverAddress = serverAddress;
    this.login = login;
    this.sc = SocketChannel.open();
    this.selector = Selector.open();
  }

  public static void main(String[] args) throws NumberFormatException, IOException {
    if (args.length != 3) {
      usage();
      return;
    }
    new Client(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
  }
  
  private static void usage() {
    System.out.println("Usage : ClientChat login hostname port");
  }
  
  public String serverHostName() {
    return serverAddress.getHostName();
  }
  
  public String login() {
    return login;
  }
  
  /**
   * Send instructions to the selector via a BlockingQueue and wake it up
   *
   * @param msg
   * @throws InterruptedException
   */
  public void sendMessage(String msg) throws InterruptedException {
    commandsQueue.put(msg);
    selector.wakeup();
  }
  
  public List<Message> getLastMessages(int n) {
    var messages = new ArrayList<Message>();
    receivedMessages.drainTo(messages, n);
    return messages;
  }
  
  /**
   * Processes the command from the BlockingQueue
   */
  private void processCommands() {
    var command = commandsQueue.poll();
    if (command != null) {
      uniqueContext.queueMessage(new Message(login, command, System.currentTimeMillis()));
    }
  }
  
  public void subscribe(Consumer<Message> subscriber) {
    Objects.requireNonNull(subscriber);
    uniqueContext.messageConsumer = subscriber;
  }
  
  public void launch() throws IOException {
    sc.configureBlocking(false);
    var key = sc.register(selector, SelectionKey.OP_CONNECT);
    uniqueContext = new Context(key);
    key.attach(uniqueContext);
    sc.connect(serverAddress);
    
    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
        processCommands();
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }
  
  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isConnectable()) {
        uniqueContext.doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        uniqueContext.doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        uniqueContext.doRead();
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
  }
  
  private void silentlyClose(SelectionKey key) {
    Channel sc = key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }
  
  static private class Context {
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer processingMsg = ByteBuffer.allocate(2 * Integer.BYTES + 2 * BUFFER_SIZE);
    private final ArrayDeque<Message> queue = new ArrayDeque<>();
    private final MessageReader messageReader = new MessageReader();
    private boolean closed = false;
    private Consumer<Message> messageConsumer = null;
    
    private Context(SelectionKey key) {
      this.key = key;
      this.sc = (SocketChannel) key.channel();
    }
    
    /**
     * Process the content of bufferIn
     * <p>
     * The convention is that bufferIn is in write-mode before the call to process
     * and after the call
     */
    private void processIn() {
      for (; ; ) {
        Reader.ProcessStatus status = messageReader.process(bufferIn);
        switch (status) {
          case DONE:
            var msg = messageReader.get();
            if(messageConsumer == null){
              logger.info("C'est null");
            }else {
              messageConsumer.accept(msg);
              messageReader.reset();
              
            }
            break;
          case REFILL:
            return;
          case ERROR:
            silentlyClose();
            return;
        }
      }
    }
    
    /**
     * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
     */
    private void queueMessage(Message msg) {
      queue.addFirst(msg);
      processOut();
      updateInterestOps();
    }
    
    /**
     * Try to fill bufferOut from the message queue
     */
    private void processOut() {
      processingMsg.flip();
      if (processingMsg.hasRemaining()) {
        var oldlimit = processingMsg.limit();
        processingMsg.limit(bufferOut.remaining());
        bufferOut.put(processingMsg);
        processingMsg.limit(oldlimit);
        processingMsg.compact();
      } else {
        processingMsg.clear();
        var msg = queue.pollLast();
        var login = StandardCharsets.UTF_8.encode(msg.login());
        var txt = StandardCharsets.UTF_8.encode(msg.txt());
        bufferOut
            .putInt(login.remaining())
            .put(login)
            .putInt(txt.remaining())
            .put(txt);
      }
      updateInterestOps();
    }
    
    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */
    
    private void updateInterestOps() {
      int ops = 0;
      if (bufferIn.hasRemaining() && !closed) {
        ops |= SelectionKey.OP_READ;
      }
      if (bufferOut.position() > 0) {
        ops |= SelectionKey.OP_WRITE;
      }
      if (ops != 0) {
        key.interestOps(ops);
      } else {
        silentlyClose();
      }
    }
    
    private void silentlyClose() {
      try {
        sc.close();
      } catch (IOException e) {
        // ignore exception
      }
    }
    
    /**
     * Performs the read action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    private void doRead() throws IOException {
      if (sc.read(bufferIn) == -1) {
        closed = true;
        logger.info("Client " + sc.getRemoteAddress() + " has closed the connection");
      }
      processIn();
      updateInterestOps();
    }
    
    /**
     * Performs the write action on sc
     * <p>
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     */
    
    private void doWrite() throws IOException {
      bufferOut.flip();
      sc.write(bufferOut);
      bufferOut.compact();
      processIn();
      updateInterestOps();
    }
    
    public void doConnect() throws IOException {
      if (!sc.finishConnect()) {
        logger.warning("the selector gave a bad hint");
        return;
      }
      key.interestOps(SelectionKey.OP_READ);
      logger.info("** Ready to chat now **");
    }
  }
}
