package fr.uge.chadow.core.context;

import fr.uge.chadow.client.Client;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.reader.FrameReader;
import fr.uge.chadow.core.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public sealed abstract class SuperContext permits ClientContext, ServerContext {
  
  private static final Logger logger = Logger.getLogger(Client.class.getName());
  public final ArrayDeque<Frame> queue = new ArrayDeque<>();
  private final SelectionKey key;
  private final SocketChannel sc;
  private final ByteBuffer bufferIn;
  private final ByteBuffer bufferOut;
  private final FrameReader frameReader = new FrameReader();
  private ByteBuffer processingFrame;
  private final Opcode currentOpcode = null;
  private boolean closed = false;
  
  public SuperContext(SelectionKey key, int BUFFER_SIZE) {
    this.key = key;
    this.sc = (SocketChannel) key.channel();
    bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
  }
  
  /**
   * Process the content of bufferIn
   * <p>
   * The convention is that bufferIn is in write-mode before the call to process
   * and after the call
   */
  private void processIn() {
    for (; ; ) {
      Reader.ProcessStatus status = frameReader.process(bufferIn);
      
      switch (status) {
        case DONE -> {
          try {
            processCurrentOpcodeActionImpl();
          } catch (IOException e) {
            logger.severe(STR."Error while processing opcode \{currentOpcode}");
            return;
          }
          frameReader.reset();
        }
        case REFILL -> {
          return;
        }
        case ERROR -> {
          silentlyClose();
          return;
        }
      }
    }
  }
  
  /**
   * Processes the current opcode received from the client and performs the corresponding action.
   * The action performed depends on the value of the current opcode.
   *
   * @throws IOException if an I/O error occurs while processing the opcode.
   */
  abstract void processCurrentOpcodeAction(Frame frame) throws IOException;
  
  private void processCurrentOpcodeActionImpl() throws IOException {
    processCurrentOpcodeAction(frameReader.get());
  }
  
  
  public void queueFrame(Frame frame) {
    queue.addFirst(frame);
    processOut();
    updateInterestOps();
  }
  
  /**
   * Try to fill bufferOut from the message queue
   */
  void processOut() {
    if (processingFrame == null && !queue.isEmpty()) {
      while (!queue.isEmpty()) {
        processingFrame = queue.pollLast()
                               .toByteBuffer();
        processingFrame.flip();
        if (processingFrame.remaining() <= bufferOut.remaining()) {
          // If enough space in bufferOut, add the frame
          bufferOut.put(processingFrame);
          processingFrame.compact();
        } else {
          processingFrame.compact();
          break;
        }
      }
    } else if (processingFrame == null) {
      // No frame currently being processed or in the queue, exit the method
      return;
    }
    
    // Processing the current frame being handled
    processingFrame.flip();
    if (processingFrame.hasRemaining()) {
      var oldlimit = processingFrame.limit();
      processingFrame.limit(Math.min(oldlimit, bufferOut.remaining()));
      bufferOut.put(processingFrame);
      processingFrame.limit(oldlimit);
      if (!processingFrame.hasRemaining()) {
        // If the frame has been fully processed, reset processingFrame to null
        processingFrame = null;
      } else {
        // If the frame has not been fully processed, compact it to keep the remaining data
        processingFrame.compact();
      }
    } else {
      // If the current frame does not contain any data, reset processingFrame to null
      processingFrame = null;
    }
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
  
  void silentlyClose() {
    try {
      sc.close();
      closed = true;
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
  public void doRead() throws IOException {
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
  
  public void doWrite() throws IOException {
    sc.write(bufferOut.flip());
    bufferOut.compact();
    processIn();
    updateInterestOps();
  }
  
  public void doConnect() throws IOException {
    if (!sc.finishConnect()) {
      logger.warning("the selector gave a bad hint");
    }
  }
  
  SelectionKey getKey() {
    return key;
  }
  
  protected SocketChannel getSocket() {
    return sc;
  }
}