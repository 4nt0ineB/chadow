package fr.uge.chadow.server;

import fr.uge.chadow.core.protocol.Message;
import fr.uge.chadow.core.reader.MessageReader;
import fr.uge.chadow.core.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class Session {
  private static final Logger logger = Logger.getLogger(ServerChaton.class.getName());
  private static final int BUFFER_SIZE = 1_024;
  private final SelectionKey key;
  private final SocketChannel sc;
  private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer processingMsg = ByteBuffer.allocate(2 * Integer.BYTES + 2 * BUFFER_SIZE);
  private final ArrayDeque<Message> queue = new ArrayDeque<>();
  private final ServerChaton server;  // we could also have Context as an instance class, which would naturally
  // give access to ServerChatInt.this
  private boolean closed = false;
  private final MessageReader messageReader = new MessageReader();

  private String login;

  Session(ServerChaton server, SelectionKey key) {
    this.key = key;
    this.sc = (SocketChannel) key.channel();
    this.server = server;
  }

  /**
   * Process the content of bufferIn
   * <p>
   * The convention is that bufferIn is in write-mode before the call to process and
   * after the call
   */
  private void processIn() {
    for (; ; ) {
      Reader.ProcessStatus status = Reader.ProcessStatus.ERROR;
      if (!isAuthenticated()) {
        // si c pas un opcode register on ferme la connexion
        status = messageReader.process(bufferIn);
      } else {
        // to get it work for now
        status = messageReader.process(bufferIn);
      }
      switch (status) {
        case DONE:
          var value = messageReader.get();
          server.broadcast(value);
          System.err.println(value);
          messageReader.reset();
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
   *
   * @param msg
   */
  public void queueMessage(Message msg) {
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
              .putInt(login.remaining()).put(login)
              .putInt(txt.remaining()).put(txt);
    }
    updateInterestOps();
  }

  /**
   * Update the interestOps of the key looking only at values of the boolean
   * closed and of both ByteBuffers.
   * <p>
   * The convention is that both buffers are in write-mode before the call to
   * updateInterestOps and after the call. Also, it is assumed that the process has
   * been called just before updateInterestOps.
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

  public boolean isAuthenticated() {
    return login != null;
  }

  /**
   * Performs the read action on sc
   * <p>
   * The convention is that both buffers are in write-mode before the call to
   * doRead and after the call
   *
   * @throws IOException
   */
  void doRead() throws IOException {
    if (sc.read(bufferIn) == -1) {
      closed = true;
      logger.info(STR."Client \{sc.getRemoteAddress()} has closed the connection");
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

  void doWrite() throws IOException {
    bufferOut.flip();
    sc.write(bufferOut);
    bufferOut.compact();
    processIn();
    updateInterestOps();
  }

}
