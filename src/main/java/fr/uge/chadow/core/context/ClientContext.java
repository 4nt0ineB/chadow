package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientContextHandler;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.Discovery;
import fr.uge.chadow.core.protocol.client.Register;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.OK;
import fr.uge.chadow.core.protocol.server.RequestResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.logging.Logger;

public final class ClientContext extends Context {
  private static final Logger logger = Logger.getLogger(ClientContextHandler.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  
  public ClientContext(SelectionKey key, ClientAPI api) {
    super(key, BUFFER_SIZE);
    this.api = api;
  }
  
  @Override
  public void processCurrentOpcodeAction(Frame frame) {
    logger.info("Processing frame");
    switch (frame) {
      case OK ok -> {
        logger.info("Connected to the server");
        api.bindContext(this);
        
        super.addFrame(new Discovery());
        super.processOut();
      }
      case YellMessage yellMessage -> api.addMessage(yellMessage);
      case WhisperMessage whisperMessage -> api.addIncomingDM(whisperMessage);
      case RequestResponse requestResponse -> {
        logger.info(STR."Received RequestResponse cdx: \{requestResponse.codex().id()}");
        api.saveFetchedCodex(requestResponse.codex());
      }
      case DiscoveryResponse discoveryResponse -> {
        logger.info(STR."Received discovery response (\{discoveryResponse.usernames().length} users)");
        api.addUserFromDiscovery(List.of(discoveryResponse.usernames()));
      }
      case Event event -> {
        if(event.code() == (byte) 0) {
          logger.info("Received event 0");
          api.removeUser(event.username());
        } else {
          logger.warning(STR."Received event \{event.code()}");
          api.addUser(event.username());
        }
      }
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
    var add = (InetSocketAddress) getSocket().getLocalAddress();
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