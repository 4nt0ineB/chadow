package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.ProxyNodeSocket;

import java.nio.ByteBuffer;

public record ClosedDownloadResponse(ProxyNodeSocket[] proxies) implements Frame {

  @Override
  public ByteBuffer toByteBuffer() {
    var proxiesByteBuffersArray = new ByteBuffer[proxies.length];
    var bufferCapacity = 0;
    for (int i = 0; i < proxies.length; i++) {
      proxiesByteBuffersArray[i] = proxies[i].toByteBuffer()
              .flip();
      bufferCapacity += proxiesByteBuffersArray[i].remaining();
    }
    var bbClosedDownloadResponse = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bufferCapacity);
    bbClosedDownloadResponse.put(Opcode.toByte(this.getClass()))
            .putInt(proxies.length);
    for (var proxy : proxiesByteBuffersArray) {
      bbClosedDownloadResponse.put(proxy);
    }
    return bbClosedDownloadResponse;
  }
}