package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.CodexStatus;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.reader.FrameReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * Context for when the app is downloading files from another client
 */
public final class DownloaderContext extends Context {
  private static final Logger logger = Logger.getLogger(DownloaderContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final InetSocketAddress sharerAddress;
  private final ClientAPI api;
  private final CodexStatus codexStatus;
  private final SelectionKey key;
  private final Integer chainId;
  private final FrameReader frameReader = new FrameReader();

  public DownloaderContext(SelectionKey key, ClientAPI api, CodexStatus codexStatus, Integer chainId) {
    super(key, BUFFER_SIZE);
    this.api = api;
    this.codexStatus = codexStatus;
    this.key = key;
    this.chainId = chainId;

    InetSocketAddress socketAddress = null;
    try {
      socketAddress = (InetSocketAddress) ((SocketChannel) key.channel()).getRemoteAddress();
    } catch (IOException e) {
      silentlyClose();
    }
    this.sharerAddress = socketAddress;
  }

  @Override
  void processCurrentOpcodeAction(Frame frame) {
    switch (frame) {
      case Denied denied -> {
        logger.warning(STR."Sharer denied sharing codex \{denied.codexId()}");
        silentlyClose();
      }
      case HereChunk hereChunk -> {
        logger.info(STR."Received chunk (\{hereChunk.offset()},\{hereChunk.payload().length})");
        if (downloadForbidden()) {
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
        if (codexStatus.isComplete()) {
          silentlyClose();
          return;
        }
        // request next chunk
        var chunk = codexStatus.nextRandomChunk();
        if (chunk != null) {
          send(new NeedChunk(chunk.offset(), chunk.length()));
        }
      }
      case Hidden hidden -> {
        logger.info("Received hidden frame");
        // we received a response from our hidden download request
        var payload = ByteBuffer.allocate(hidden.payload().length)
                .put(hidden.payload());
        if (frameReader.process(payload) != FrameReader.ProcessStatus.DONE) {
          logger.warning("Error while processing hidden frame");
          silentlyClose();
        }
        processCurrentOpcodeAction(frameReader.get());
        frameReader.reset();
      }
      default -> {
        logger.warning("No action for the received frame");
        silentlyClose();
      }
    }
  }

  private boolean downloadForbidden() {
    if (codexStatus == null) {
      return true;
    }
    if (!codexStatus.isDownloading()) {
      return true;
    }
    if (!api.codexExists(codexStatus.codex().id())) {
      logger.warning(STR."Codex \{codexStatus.codex().id()} does not exist");
      return true;
    }
    if (codexStatus.isComplete()) {
      logger.info(STR."Codex \{codexStatus.codex().id()} is complete");
      return true;
    }
    if (chainId == null && codexStatus.isDownloadingHidden()) {
      logger.info(STR."The current downloader was downloading Codex \{codexStatus.codex().id()} with open mode, but the codex is now hidden");
      return true;
    }
    return false;
  }

  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    var port = ((InetSocketAddress) ((SocketChannel) key.channel()).getRemoteAddress()).getPort();
    logger.info(STR."opening connection with a sharer for the codex \{codexStatus.codex().id()} on port \{port}");
    initDownload();
  }

  private void send(Frame frame) {
    if (chainId != null) {
      var buffer = frame.toByteBuffer();
      frame = new Hidden(chainId, buffer.array());
    }
    queueFrame(frame);
  }

  private void initDownload() {
    if (downloadForbidden()) {
      silentlyClose();
      return;
    }
    var handshake = new Handshake(codexStatus.codex().id());
    var chunk = codexStatus.nextRandomChunk();
    var needChunk = new NeedChunk(chunk.offset(), chunk.length());
    if (chainId != null) {
      addFrame(new Hidden(chainId, handshake.toByteBuffer().array()));
      addFrame(new Hidden(chainId, needChunk.toByteBuffer().array()));
    } else {
      addFrame(handshake);
      addFrame(needChunk);
    }
    api.registerDownloader(codexStatus.codex().id(), sharerAddress);
    processOut();
    getKey().interestOps(SelectionKey.OP_WRITE);
  }

  @Override
  public void silentlyClose() {
    super.silentlyClose();
    api.unregisterDownloader(codexStatus.codex().id(), sharerAddress);
  }
}