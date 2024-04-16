package fr.uge.chadow.core.protocol.field;

import java.nio.ByteBuffer;

public record SocketField(byte[] ip, int port) {
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(Integer.BYTES + ip.length + Integer.BYTES);
    return bb.putInt(ip.length).put(ip).putInt(port);
  }
}