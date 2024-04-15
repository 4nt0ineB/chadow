package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.client.CodexStatus;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.server.OK;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * Context for when the app is downloading files from another client
 */
public final class DownloaderContext extends Context {
  private static final Logger logger = Logger.getLogger(DownloaderContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  private final CodexStatus codexStatus;
  private final SelectionKey key;
  
  
  public DownloaderContext(SelectionKey key, ClientAPI api, CodexStatus codexStatus) {
    super(key, BUFFER_SIZE);
    this.api = api;
    this.codexStatus = codexStatus;
    this.key = key;
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Denied denied -> {
        logger.warning(STR."Sharer denied sharing codex \{denied.codexId()}");
        super.silentlyClose();
      }
      case HereChunk hereChunk -> {
        logger.info(STR."Received chunk (\{hereChunk.offset()},\{hereChunk.payload().length})");
        if(!canDownload()) {
          silentlyClose();
          return;
        }
        try {
          api.writeChunk(codexStatus.id(), hereChunk.offset(), hereChunk.payload());
        } catch (IOException e) {
          logger.severe(STR."Error while writing chunk \{hereChunk.offset()} for codex \{codexStatus.codex().id()} : \{e.getCause()}");
          silentlyClose();
          return;
        }
        if(codexStatus.isComplete()) {
          silentlyClose();
          return;
        }
        // request next chunk
        var chunk = codexStatus.nextRandomChunk();
        if (chunk != null) {
          queueFrame(new NeedChunk(chunk.offset(), chunk.length()));
        }
      }
      default -> {
        logger.warning("No action for the received frame");
        silentlyClose();
      }
    }
  }
  
  private boolean canDownload() {
    if(codexStatus == null) {
      return false;
    }
    if(!api.codexExists(codexStatus.codex().id())) {
      logger.warning(STR."Codex \{codexStatus.codex().id()} does not exist");
      return false;
    }
    if(codexStatus.isComplete()) {
      logger.info(STR."Codex \{codexStatus.codex().id()} is complete");
      return false;
    }
    return true;
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    var port = ((InetSocketAddress) ((SocketChannel) key.channel()).getRemoteAddress()).getPort();
    logger.info(STR."opening connection with a sharer for the codex \{codexStatus.codex().id()} on port \{port}");
    addFrame(new Handshake(codexStatus.codex().id()));
    var chunk = codexStatus.nextRandomChunk();
    addFrame(new NeedChunk(chunk.offset(), chunk.length()));
    processOut();
    getKey().interestOps(SelectionKey.OP_WRITE);
  }
  
}