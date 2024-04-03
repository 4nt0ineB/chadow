package fr.uge.chadow.server;

import fr.uge.chadow.core.protocol.Message;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.Register;
import fr.uge.chadow.core.reader.GlobalReader;
import fr.uge.chadow.core.reader.MessageReader;
import fr.uge.chadow.core.reader.Reader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Session {
  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private static final int BUFFER_SIZE = 1_024;
  private final ArrayDeque<Message> queue = new ArrayDeque<>();
  private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
  private final ByteBuffer processingMsg = ByteBuffer.allocate(2 * Integer.BYTES + 2 * BUFFER_SIZE);
  private final Map<Opcode, Reader<?>> readers = new HashMap<>();
  private final SelectionKey key;
  private final Server server;  // we could also have Context as an instance class, which would naturally
  // give access to ServerChatInt.this
  private final SocketChannel sc;
  private boolean closed = false;
  private String login;
  private Opcode currentOpcode;

  Session(Server server, SelectionKey key) {
    this.key = key;
    this.sc = (SocketChannel) key.channel();
    this.server = server;

    for (var opcode : Opcode.values()) {
      switch (opcode) {
        case REGISTER -> readers.put(opcode, new GlobalReader<>(Register.class));
        case YELL -> readers.put(opcode, new GlobalReader<>(Message.class));
        default -> {
          logger.warning(STR."No reader for opcode \{opcode}");
          silentlyClose();
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
      if (!validateClientOperation()) {
        silentlyClose();
        return;
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
   * Validates the current opcode and checks if the client is authenticated.
   * If the current opcode has already been determined to be valid and the client is authenticated,
   * returns true.
   * Otherwise, reads the opcode from the input buffer, validates it, and checks if authentication is required.
   *
   * @return true if the current opcode is valid and, if required, the client is authenticated, otherwise false.
   */
  private boolean validateClientOperation() {
    if (currentOpcode != null) {
      return true;
    }

    bufferIn.flip();
    if (bufferIn.remaining() < Byte.BYTES) {
      logger.warning("Not enough bytes for opcode");
      bufferIn.compact();
      return false;
    }

    var byteOpCode = bufferIn.get();
    bufferIn.compact();

    try {
      currentOpcode = Opcode.from(byteOpCode);
    } catch (IllegalArgumentException e) {
      logger.warning("Invalid opcode");
      return false;
    }

    if (Opcode.REGISTER != currentOpcode && !isAuthenticated()) {
      logger.warning("Client not authenticated");
      return false;
    }

    if (Opcode.REGISTER == currentOpcode && isAuthenticated()) {
      logger.warning("Client already authenticated");
      return false;
    }

    return true;
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
        server.addClient(login, sc);
        logger.info(STR."Client \{sc.getRemoteAddress()} has logged in as \{login}");
      }
      case YELL -> {
        var message = (Message) readers.get(currentOpcode).get();
        server.broadcast(message);
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
    sc.write(bufferOut.flip());
    bufferOut.compact();
    processOut();
    updateInterestOps();
  }

}
