package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record ProxyOk(int chainId) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
    return bb.put(Opcode.toByte(this.getClass()))
             .putInt(chainId);
  }
}