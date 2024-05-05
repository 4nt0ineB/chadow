package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.reader.FrameReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayDeque;
import java.util.logging.Logger;

/**
 * Context for when the app is sharing files to another client
 */
public final class ClientAsServerContext extends Context implements ProxyBridgeLeftSideContext {
  private static final Logger logger = Logger.getLogger(ClientAsServerContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  private String wantedCodexId;
  private final int maxAcceptedChunkSize;
  private InetSocketAddress clientAddress;
  private final FrameReader frameReader = new FrameReader();
  // proxy
  private Integer chainId;
  private Context bridgeRightSide;
  private boolean isClosed;
  private final ArrayDeque<Frame> framesForTheNextHop = new ArrayDeque<>();
  private boolean isProxy = false;

  public ClientAsServerContext(SelectionKey key, ClientAPI api, int maxAcceptedChunkSize) {
    super(key, BUFFER_SIZE);
    this.api = api;
    this.maxAcceptedChunkSize = maxAcceptedChunkSize;
  }

  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Handshake handshake -> {
        wantedCodexId = handshake.codexId();
        if (allowedToShare()) {
          logger.info(STR."Ready to share codex \{wantedCodexId}");
          clientAddress = (InetSocketAddress) getSocket().getRemoteAddress();
          api.registerSharer(wantedCodexId);
        } else {
          logger.info("Client wants to download a codex that is not shared");
          clearFrameQueue();
          send(new Denied(wantedCodexId));
          silentlyClose();
        }
      }
      case NeedChunk needChunk -> {
        logger.info(STR."\{clientAddress} needs chunk (\{needChunk.offset()},\{needChunk.length()})");
        if (!allowedToShare()) {
          silentlyClose();
        }
        if (needChunk.length() > maxAcceptedChunkSize) {
          logger.warning("Client requested a too big chunk");
          silentlyClose();
        }
        try {
          var chunkPayload = api.getChunk(wantedCodexId, needChunk.offset(), needChunk.length());
          send(new HereChunk(needChunk.offset(), chunkPayload));
        } catch (IOException e) {
          logger.warning(e.getMessage());
          silentlyClose();
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      case ProxyOpen proxyOpen -> {
        logger.info("Received proxy open request");
        chainId = proxyOpen.chainId();
        api.setUpBridge(chainId, this);
      }
      case Hidden hidden -> {
        logger.info("Received hidden frame");
        if (chainId == null) {
          // first frame received
          // bridgeContext not set yet;
          chainId = hidden.chainId();
          // if a routing exists, we are a proxy
          isProxy = api.setUpBridge(chainId, this);
          logger.info(STR."Client is a \{isProxy ? "proxy" : "sharer"}");
        }

        if (isProxy && bridgeRightSide == null) {
          // we are a proxy, but the bridge is not set yet
          // queue the frame
          framesForTheNextHop.addLast(hidden);
        } else if (isProxy) {
          // we are a proxy and the bridge is set
          // forward the frame to the bridge
          bridgeRightSide.queueFrame(hidden);
        } else {
          // we are a sharer
          // extract payload and processIt
          var payload = ByteBuffer.allocate(hidden.payload().length)
                  .put(hidden.payload());
          if (frameReader.process(payload) != FrameReader.ProcessStatus.DONE) {
            logger.warning("Error while processing hidden frame");
            silentlyClose();
          }
          processCurrentOpcodeAction(frameReader.get());
          frameReader.reset();
        }
      }
      default -> {
        logger.warning("No action for the received frame");
        silentlyClose();
      }
    }
  }

  private void send(Frame frame) {
    if (chainId != null) {
      var buffer = frame.toByteBuffer();
      frame = new Hidden(chainId, buffer.array());
    }
    queueFrame(frame);
  }

  private boolean allowedToShare() {
    assert wantedCodexId != null;
    return api.codexExists(wantedCodexId)
            && api.isSharing(wantedCodexId)
            && !isProxy;
  }

  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info("Received connection from a client");
  }

  public void setBridge(Context bridgeRightSide) {
    this.bridgeRightSide = bridgeRightSide;
    api.registerProxy();
    // send queued frames
    while (!framesForTheNextHop.isEmpty()) {
      this.bridgeRightSide.queueFrame(framesForTheNextHop.pollFirst());
    }
  }

  @Override
  public void silentlyClose() {
    super.silentlyClose();
    if (!isClosed && chainId != null && bridgeRightSide != null) {
      // close the bridge
      isClosed = true;
      bridgeRightSide.silentlyClose();
      bridgeRightSide = null;
      api.unregisterProxy();
    }
    if (!isProxy) {
      api.unregisterSharer(wantedCodexId);
    }
  }
}