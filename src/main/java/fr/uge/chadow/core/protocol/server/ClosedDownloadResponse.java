package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.field.ProxyNodeSocket;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.nio.ByteBuffer;

public record ClosedDownloadResponse(ProxyNodeSocket[] proxies) implements Frame{
  
  @Override
  public ByteBuffer toByteBuffer() {
    throw new Error("Not implemented");
  }
}