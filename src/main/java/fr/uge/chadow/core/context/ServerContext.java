package fr.uge.chadow.core.context;

import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.field.SocketField;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.OK;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public final class ServerContext extends Context implements ProxyBridgeLeftSideContext {
  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private static final int BUFFER_SIZE = 1_024;
  private final Server server;
  private boolean closed = false;
  private String login;
  private SocketField serverPublicAddress;
  // proxy
  private Integer chainId;
  private Context bridgeRightSide;
  private boolean isClosed;
  private final ArrayDeque<Frame> framesForTheNextHop = new ArrayDeque<>();
  private boolean isProxy = false;
  

  public ServerContext(Server server, SelectionKey key) {
    super(key, BUFFER_SIZE);
    this.server = server;
  }

  @Override
  public void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Register register -> {
        if (isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is already authenticated");
          silentlyClose();
          return;
        }

        login = register.username();
        serverPublicAddress = register.serverPublicAddress();
        var remoteInetSocketAddress = (InetSocketAddress) super.getSocket().getRemoteAddress();
        var listeningAddress = new InetSocketAddress(remoteInetSocketAddress.getAddress(), register.listenerPort());

        if (!server.addClient(login, super.getSocket(), listeningAddress, this)) {
          logger.warning(STR."Login \{login} already in use");
          silentlyClose();
          return;
        }

        logger.info(STR."Client \{super.getSocket().getRemoteAddress()} has logged in as \{login}");

        // Send an OK message to the client
        queueFrame(new OK());
        server.broadcast(new Event((byte) 1, login));
      }

      case Discovery _ -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.discovery(this);
      }

      case YellMessage yellMessage -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }

        var newMessage = new YellMessage(yellMessage.login(), yellMessage.txt(), System.currentTimeMillis());
        server.broadcast(newMessage);
      }

      case WhisperMessage whisperMessage -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.whisper(whisperMessage, login);
      }

      case Propose propose -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.propose(propose.codex(), login);
      }

      case Request request -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.request(request.codexId(), this);
      }

      case RequestDownload requestDownload -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        if (requestDownload.mode() == 0) {
          server.requestOpenDownload(this, requestDownload.codexId(), requestDownload.numberOfSharers());
        } else {
          server.requestClosedDownload(this, requestDownload);
        }
      }

      case Search search -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        logger.info(STR."Searching for \{search.codexName()}");
        var result = server.search(search);
        queueFrame(result);
        logger.info(STR."Get \{result.results().length} results");
      }

      case ProxyOk proxyOk -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }

        server.proxyOk(this, proxyOk.chainId());
      }
      
      case Hidden hidden -> {
        logger.info("Received hidden frame");
        if(chainId == null) {
          // first frame received
          // bridgeContext not set yet;
          chainId = hidden.chainId();
          // if a routing exists, we are a proxy
          isProxy = server.setUpBridge(chainId, this);
          logger.info(STR."Client is a \{isProxy ? "proxy" : "sharer"}");
        }
        
        if(isProxy && bridgeRightSide == null){
          // we are a proxy, but the bridge is not set yet
          // queue the frame
          framesForTheNextHop.addLast(hidden);
        } else if (isProxy) {
          // we are a proxy and the bridge is set
          // forward the frame to the bridge
          bridgeRightSide.queueFrame(hidden);
        }
      }
      
      case Update update -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.update(update.codexId(), login);
      }
      
      default -> {
        logger.warning("No action for the received frame ");
        silentlyClose();
      }
    }
  }

  public String login() {
    return login;
  }
  
  public SocketField getServerPublicAddress() {
    return serverPublicAddress;
  }

  private boolean isAuthenticated() {
    return login != null;
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info(STR."client is connecting");
  }
  
  @Override
  public void setBridge(Context bridgeRightSide) {
    this.bridgeRightSide = bridgeRightSide;
    // send queued frames
    while(!framesForTheNextHop.isEmpty()) {
      this.bridgeRightSide.queueFrame(framesForTheNextHop.pollFirst());
    }
  }
  
  @Override
  public void silentlyClose() {
    if (login != null) { // when the client is a downloader
      server.removeClient(login);
    }
    if(!isClosed && chainId != null && bridgeRightSide != null) {
      // close the bridge
      isClosed = true;
      bridgeRightSide.silentlyClose();
      bridgeRightSide = null;
    }
    super.silentlyClose();
  }
}