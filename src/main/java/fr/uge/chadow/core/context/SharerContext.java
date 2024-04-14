package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.core.protocol.Frame;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

/**
 * Context for when the app is sharing files to another client
 */
public final class SharerContext extends Context {
  private static final Logger logger = Logger.getLogger(SharerContext.class.getName());
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  
  
  public SharerContext(SelectionKey key, ClientAPI api) {
    super(key, BUFFER_SIZE);
    this.api = api;
    // addFrame(new HandShake());
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
    /*
      switch
        HANDSHAKE,
        DENIED,
        NEEDCHUNK,
        CANCEL,
        etc...
     */
  }
  
  @Override
  public void doConnect() throws IOException {
    super.doConnect();
    logger.info("Received connection from a client");
  }
}