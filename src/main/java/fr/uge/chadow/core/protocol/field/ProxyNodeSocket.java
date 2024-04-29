package fr.uge.chadow.core.protocol.field;

import java.nio.ByteBuffer;

public record ProxyNodeSocket(SocketField socket, int chainId) {
  public ByteBuffer toByteBuffer() {
    var bbSocket = socket.toByteBuffer().flip();
    var bb = ByteBuffer.allocate(Integer.BYTES + bbSocket.remaining());
    return bb.put(bbSocket).putInt(chainId);
  }
}