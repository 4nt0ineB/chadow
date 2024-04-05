package fr.uge.chadow.core.context;

import fr.uge.chadow.client.Client;
import fr.uge.chadow.core.protocol.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

public final class ClientContext extends SuperContext {
  private static final Logger logger = Logger.getLogger(Client.class.getName());
  private static final int BUFFER_SIZE = 1024;
  
  
  private final Client client;
  
  private boolean isConnected = false;
  
  public ClientContext(SelectionKey key, Client client) {
    super(key, client, BUFFER_SIZE);
    this.client = client;
  }
  
  public boolean isConnected() {
    return isConnected;
  }
  
  @Override
  public void processCurrentOpcodeAction(Frame frame) {
    switch (frame) {
      case OK ok -> {
        isConnected = true;
        logger.info("Connected to the server");
      }
      case YellMessage yellMessage -> client.addMessage(yellMessage);
      case WhisperMessage whisperMessage -> client.addWhisper(whisperMessage);
      default -> {
        logger.warning("No action for the received frame");
        super.silentlyClose();
      }
    }
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    queue.addFirst(new Register(client.login()));
    getKey().interestOps(SelectionKey.OP_WRITE);
    super.processOut();
    logger.info("** Ready to chat now **");
  }
}