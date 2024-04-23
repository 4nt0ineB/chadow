package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.nio.ByteBuffer;

public record Proxy(int chainId, SocketField socket) implements Frame {
  
  @Override
  public ByteBuffer toByteBuffer() {
    var socketBuffer = socket.toByteBuffer();
    socketBuffer.flip();
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + socketBuffer.remaining());
    bb.put(Opcode.toByte(this.getClass()))
    .putInt(chainId)
    .put(socketBuffer);
    return bb;
  }
}