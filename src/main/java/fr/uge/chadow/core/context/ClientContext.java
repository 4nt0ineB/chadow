package fr.uge.chadow.core.context;

import fr.uge.chadow.client.Client;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.Register;
import fr.uge.chadow.core.protocol.client.Request;
import fr.uge.chadow.core.protocol.server.OK;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

public final class ClientContext extends Context {
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
      case WhisperMessage whisperMessage -> api.addIncomingDM(whisperMessage);
      case fr.uge.chadow.core.protocol.server.Request request -> {} //api.fetchCodex(request);
      default -> {
        logger.warning("No action for the received frame");
        super.silentlyClose();
      }
    }
  }
  
  @Override
  public void doConnect() throws IOException {
    try {
      super.doConnect();
    } catch (IOException e) {
      api.close();
    }
    super.addFrame(new Register(api.login()));
    getKey().interestOps(SelectionKey.OP_WRITE);
    super.processOut();
  }
  
  @Override
  public void silentlyClose() {
    super.silentlyClose();
    api.unbindContext();
  }
}