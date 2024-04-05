package fr.uge.chadow.core.context;

import fr.uge.chadow.client.Client;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.protocol.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

public final class ClientContext extends SuperContext {
  private static final Logger logger = Logger.getLogger(Client.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  
  public ClientContext(SelectionKey key, ClientAPI api) {
    super(key, BUFFER_SIZE);
    this.api = api;
  }
  
  @Override
  public void processCurrentOpcodeAction(Frame frame) {
    switch (frame) {
      case OK ok -> {
        logger.info("Connected to the server");
        api.bindContext(this);
      }
      case YellMessage yellMessage -> api.addMessage(yellMessage);
      case WhisperMessage whisperMessage -> api.addWhisper(whisperMessage);
      default -> {
        logger.warning("No action for the received frame");
        super.silentlyClose();
      }
    }
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    super.addFrame(new Register(api.login()));
    getKey().interestOps(SelectionKey.OP_WRITE);
    super.processOut();
    logger.info("** Ready to chat now **");
  }
  
  @Override
  public void silentlyClose() {
    super.silentlyClose();
    api.unbindContext();
  }
}