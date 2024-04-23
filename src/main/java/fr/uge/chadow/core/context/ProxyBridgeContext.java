package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.client.Hidden;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Context for when the app is a proxy between two clients
 * This context connect the current Client that is a proxy to the next node of the chain.
 * It receives frames from the ClientAsServerContext that are meant to be forwarded to the next node.
 * Then received frames from the next node are forwarded back to the ClientAsServerContext.
 *
 * <pre>
 *                         The current client App (being a proxy)
 * client A <---[-->(ClientServerAsContext) <--API--> (ProxyBridgeContext)<---]---> client B
 * </pre>
 */
public final class ProxyBridgeContext extends Context {
  private static final Logger logger = Logger.getLogger(ClientAsServerContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private String wantedCodexId;
  private InetSocketAddress clientAddress;
  private final ClientAsServerContext otherEnd;
  
  
  public ProxyBridgeContext(SelectionKey key, ClientAsServerContext otherEnd) {
    super(key, BUFFER_SIZE);
    this.otherEnd = otherEnd;
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    if (Objects.requireNonNull(frame) instanceof Hidden) {
      logger.info("Next hop send a Hidden frame");
      otherEnd.queueFrame(frame); // just forward the frame to the previous hop through the bridge
    } else {
      logger.warning(STR."Received unexpected frame \{frame}");
      silentlyClose();
    }
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info("Received connection from a client");
    otherEnd.setBridge(this);
  }
}