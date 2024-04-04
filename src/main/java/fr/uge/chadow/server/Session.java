package fr.uge.chadow.server;

import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.reader.ByteReader;
import fr.uge.chadow.core.reader.GlobalReader;
import fr.uge.chadow.core.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Session {
  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private static final int BUFFER_SIZE = 1_024;

  private final ArrayDeque<Frame> queue = new ArrayDeque<>();
  private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteReader byteReader = new ByteReader();
  private final Map<Opcode, Reader<?>> readers = new HashMap<>();
  private final SelectionKey key;
  private final Server server;  // we could also have Context as an instance class, which would naturally give access
  // to ServerChatInt.this

  private final SocketChannel sc;
  private boolean closed = false;
  private ByteBuffer processingFrame;
  private String login;
  private Opcode currentOpcode;

  Session(Server server, SelectionKey key) {
    this.key = key;
    this.sc = (SocketChannel) key.channel();
    this.server = server;

    for (var opcode : Opcode.values()) {
      switch (opcode) {
        case REGISTER -> readers.put(opcode, new GlobalReader<>(Register.class));
        case YELL -> readers.put(opcode, new GlobalReader<>(YellMessage.class));
        case WHISPER -> readers.put(opcode, new GlobalReader<>(WhisperMessage.class));
        default -> {
          //logger.warning(STR."No reader for opcode \{opcode}");
          //silentlyClose();
        }
      }
    }
  }

  /**
   * Process the content of bufferIn
   * <p>
   * The convention is that bufferIn is in write-mode before the call to process and
   * after the call
   */
  private void processIn() {
    for (; ; ) {
      if (currentOpcode == null) {
        Reader.ProcessStatus opcodeStatus = byteReader.process(bufferIn);
        switch (opcodeStatus) {
          case DONE -> {
            currentOpcode = Opcode.from(byteReader.get());
            byteReader.reset();

            if (Opcode.REGISTER != currentOpcode && !isAuthenticated()) {
              logger.warning("Client not authenticated");
              silentlyClose();
              return;
            }

            if (Opcode.REGISTER == currentOpcode && isAuthenticated()) {
              logger.warning("Client already authenticated");
              silentlyClose();
              return;
            }
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

      Reader.ProcessStatus status = readers.get(currentOpcode).process(bufferIn);

      switch (status) {
        case DONE -> {
          try {
            processCurrentOpcodeAction();
          } catch (IOException e) {
            logger.severe(STR."Error while processing opcode \{currentOpcode}");
            return;
          }
          readers.get(currentOpcode).reset();
          currentOpcode = null; // reset opcode
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
  private void processCurrentOpcodeAction() throws IOException {
    switch (currentOpcode) {
      case REGISTER -> {
        var register = (Register) readers.get(currentOpcode).get();
        login = register.username();
        if (!server.addClient(login, sc)) {
          logger.warning(STR."Login \{login} already in use");
          silentlyClose();
          return;
        }
        logger.info(STR."Client \{sc.getRemoteAddress()} has logged in as \{login}");

        // Send an OK message to the client
        queueFrame(new OK());
      }
      case YELL -> {
        logger.info(STR."Yell message received");
        var message = (YellMessage) readers.get(currentOpcode).get();
        logger.info(STR."Message from \{message.login()}: \{message.txt()}");
        var newMessage = new YellMessage(message.login(), message.txt(), System.currentTimeMillis());
        server.broadcast(newMessage);
      }
      case WHISPER -> {
        var message = (WhisperMessage) readers.get(currentOpcode).get();
        var newMessage = new WhisperMessage(message.username(), message.txt(), System.currentTimeMillis());
        server.whisper(newMessage);
      }
      default -> {
        logger.warning(STR."No action for opcode \{currentOpcode}");
        silentlyClose();
      }
    }
  }

  private boolean isAuthenticated() {
    return login != null;
  }

  /**
   * Add a frame to the message queue, tries to fill bufferOut and updateInterestOps
   *
   * @param frame the frame to add to the queue
   */
  public void queueFrame(Frame frame) {
    queue.addFirst(frame);
    processOut();
    System.out.println("queueFrame : Update interest ops");
    updateInterestOps();
  }

  /**
   * Try to fill bufferOut from the message queue
   */
  private void processOut() {
    System.out.println("processOut");
    System.out.println("bufferOut: " + bufferOut);
    System.out.println("queue: " + queue);
    System.out.println("processingFrame: " + processingFrame);
    if (processingFrame == null && !queue.isEmpty()) {
      while (!queue.isEmpty()) {
        processingFrame = queue.pollLast().toByteBuffer();
        processingFrame.flip();
        System.out.println("processingFrame: " + processingFrame);
        System.out.println("bufferOut remaning: " + bufferOut.remaining());
        if (processingFrame.remaining() <= bufferOut.remaining()) {
          // If enough space in bufferOut, add the frame
          bufferOut.put(processingFrame);
          processingFrame.compact();
        } else { // plus de place
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

    System.out.println("bufferOut: " + bufferOut);
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
      System.out.println("JE READ");
      System.out.println("bufferIn: " + bufferIn);
      ops |= SelectionKey.OP_READ;
    }
    if (bufferOut.position() > 0) {
      System.out.println("JE WRITE");
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
      server.removeClient(login);
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
   * @throws IOException if an I/O error occurs while reading from the socket channel
   */
  void doRead() throws IOException {
    if (sc.read(bufferIn) == -1) {
      closed = true;
      logger.info(STR."Client \{sc.getRemoteAddress()} has closed the connection");
    }
    System.out.println("Le client " + sc.getRemoteAddress() + " a envoyé: " + bufferIn);
    processIn();
    System.out.println("doRead : Update interest ops");
    updateInterestOps();
  }

  /**
   * Performs the write action on sc
   * <p>
   * The convention is that both buffers are in write-mode before the call to
   * doWrite and after the call
   *
   * @throws IOException if an I/O error occurs while writing to the socket channel
   */

  void doWrite() throws IOException {
    sc.write(bufferOut.flip());
    bufferOut.compact();
    processOut();
    System.out.println("doWrite : Update interest ops");
    updateInterestOps();
  }

}
