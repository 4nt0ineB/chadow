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
public final class ClientAsServerContext extends Context {
  private static final Logger logger = Logger.getLogger(ClientAsServerContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  private String wantedCodexId;
  private InetSocketAddress clientAddress;
  private final FrameReader frameReader = new FrameReader();
  // proxy
  private Integer chainId;
  private Context bridgeContext;
  private final ArrayDeque<Frame> framesForTheNextHop = new ArrayDeque<>();
  
  public ClientAsServerContext(SelectionKey key, ClientAPI api) {
    super(key, BUFFER_SIZE);
    this.api = api;
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Handshake handshake -> {
        wantedCodexId = handshake.codexId();
        if(allowedToShare()) {
          logger.info(STR."Ready to share codex \{wantedCodexId}");
          clientAddress = (InetSocketAddress) getSocket().getRemoteAddress();
        }else {
          logger.info("Client wants to download a codex that is not shared");
          clearFrameQueue();
          send(new Denied(wantedCodexId));
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
        if(chainId != null) {
          // we are a proxy
          if(bridgeContext == null) {
            // we have a chain, but the bridge is not set yet
            framesForTheNextHop.addLast(hidden);
          }else {
            bridgeContext.queueFrame(hidden); // forward the frame
          }
          return;
        }
        // we are a client (sharing a codex)
        // extract payload and processIt
        var payload = ByteBuffer.allocate(hidden.payload().length)
                                .put(hidden.payload());
        if(frameReader.process(payload) != FrameReader.ProcessStatus.DONE) {
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
  
  private void send(Frame frame) {
    if(chainId != null) {
      var buffer = frame.toByteBuffer();
      frame = new Hidden(chainId, buffer.array());
    }
    queueFrame(frame);
  }
  
  private boolean allowedToShare() {
    assert wantedCodexId != null;
    return api.codexExists(wantedCodexId)
        && api.isSharing(wantedCodexId)
        && chainId == null;
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info("Received connection from a client");
  }
  
  public void setBridge(Context bridgeContext) {
    this.bridgeContext = bridgeContext;
    // send queued frames
    while(!framesForTheNextHop.isEmpty()) {
      bridgeContext.queueFrame(framesForTheNextHop.pollFirst());
    }
  }
  
  @Override
  public void silentlyClose() {
    super.silentlyClose();
    if(chainId != null) {
      // close the bridge
      bridgeContext.silentlyClose();
    }
  }
}