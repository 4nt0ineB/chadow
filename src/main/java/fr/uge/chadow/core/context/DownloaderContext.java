package fr.uge.chadow.core.context;

import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.core.protocol.Frame;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public final class DownloaderContext extends Context {
  private static final int BUFFER_SIZE = 1024;
  private final ClientAPI api;
  private final CodexController.CodexStatus codexStatus;
  
  
  public DownloaderContext(SelectionKey key, ClientAPI api, CodexController.CodexStatus codexStatus) {
    super(key, BUFFER_SIZE);
    this.api = api;
    this.codexStatus = codexStatus;
    // addFrame(new HandShake());
  }
  
  @Override
  void processCurrentOpcodeAction(Frame frame) throws IOException {
  
  }
  
  
}