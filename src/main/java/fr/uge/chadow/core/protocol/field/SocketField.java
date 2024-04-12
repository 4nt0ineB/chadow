package fr.uge.chadow.core.protocol.field;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record SocketField(String ip, int port) {
  public ByteBuffer toByteBuffer() {
    var ipBuffer = UTF_8.encode(ip);
    var bb = ByteBuffer.allocate(Integer.BYTES + ipBuffer.remaining() + Integer.BYTES);
    return bb
            .putInt(ipBuffer.remaining())
            .put(ipBuffer)
            .putInt(port);
  }
}
