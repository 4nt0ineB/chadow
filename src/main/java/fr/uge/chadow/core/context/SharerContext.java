package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.WhisperMessage;
import fr.uge.chadow.core.protocol.YellMessage;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.server.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.logging.Logger;

/**
 * Context for when the app is sharing files to another client
 */
public final class SharerContext extends Context {
  private static final Logger logger = Logger.getLogger(SharerContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  private boolean introduced = false;
  private String wantedCodexId;
  private InetSocketAddress clientAddress;
  
  
  public SharerContext(SelectionKey key, ClientAPI api) {
    super(key, BUFFER_SIZE);
    this.api = api;
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Handshake handshake -> {
        wantedCodexId = handshake.codexId();
        if(allowedToShare()) {
          introduced = true;
          logger.info("Ready to share codex " + wantedCodexId);
          clientAddress = (InetSocketAddress) getSocket().getRemoteAddress();
        }else {
          logger.warning("Client wants to download a codex that is not shared");
          clearFrameQueue();
          queueFrame(new Denied(wantedCodexId));
          silentlyClose();
        }
      }
      case NeedChunk needChunk -> {
        logger.info(STR."\{clientAddress} needs chunk (\{needChunk.offset()},\{needChunk.length()})");
        if(!allowedToShare()) {
          silentlyClose();
        }
        try {
          var chunkPayload = api.getChunk(wantedCodexId, needChunk.offset(), needChunk.length());
          queueFrame(new HereChunk(needChunk.offset(), chunkPayload));
          logger.info(STR."Sent chunk (\{needChunk.offset()},\{chunkPayload.length})");
          
        } catch (IOException e) {
          logger.warning(e.getMessage());
          silentlyClose();
          return;
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      default -> {
        logger.warning("No action for the received frame");
        silentlyClose();
      }
    }
    /*
      switch
        HANDSHAKE,
        DENIED,
        NEEDCHUNK,
        CANCEL,
        etc...
     */
  }
  
  private boolean allowedToShare() {
    assert wantedCodexId != null;
    return api.codexExists(wantedCodexId) && api.isSharing(wantedCodexId);
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info("Received connection from a client");
  }
  
  /**
   * Returns the codex id of the codex the client wants to download
   * @return
   */
  public String getWantedCodexId() {
    return wantedCodexId;
  }
}