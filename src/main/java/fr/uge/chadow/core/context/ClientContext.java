package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.TCPConnectionManager;
import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.Discovery;
import fr.uge.chadow.core.protocol.client.ProxyOk;
import fr.uge.chadow.core.protocol.client.Register;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.logging.Logger;

public final class ClientContext extends Context {
  private static final Logger logger = Logger.getLogger(TCPConnectionManager.class.getName());
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
      case OK _ -> {
        logger.info("Connected to the server");
        api.bindContext(this);
        super.addFrame(new Discovery()); // fetch all users
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
      case RequestOpenDownload requestOpenDownload -> {
        logger.info(STR."Received request open download  \{requestOpenDownload.sockets().length} sockets");
        api.addSocketsOpenDownload(requestOpenDownload.sockets());
      }
      case SearchResponse searchResponse -> {
        logger.info(STR."Received search response (\{searchResponse.results().length} results)");
        api.saveSearchResponse(searchResponse);
      }
      case ClosedDownloadResponse closedDownloadResponse -> {
        logger.info(STR."Received \{closedDownloadResponse.proxies().length} proxy sockets");
        api.addSocketsClosedDownload(closedDownloadResponse.proxies());
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
      case Proxy proxy -> {
        logger.info(STR."Received proxy request chainId: \{proxy.chainId()}");
        var response = api.saveProxyRoute(proxy.chainId(), proxy.socket());
        if(response) {
          queueFrame(new ProxyOk(proxy.chainId()));
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
    var address = (InetSocketAddress) super.getSocket().getRemoteAddress();
    var socket = new SocketField(address.getAddress().getAddress(), address.getPort());
    super.addFrame(new Register(api.login(), api.listeningPort(), socket));
    getKey().interestOps(SelectionKey.OP_WRITE);
    super.processOut();
  }
  
  @Override
  public void silentlyClose() {
    super.silentlyClose();
    api.unbindContext();
  }
}