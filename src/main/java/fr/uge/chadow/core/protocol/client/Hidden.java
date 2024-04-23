package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record Hidden(int chainId, byte[] payload) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var buffer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + Integer.BYTES + payload.length);
    return buffer.put(Opcode.toByte(this.getClass()))
                  .putInt(chainId)
                  .putInt(payload.length)
                  .put(payload);
  }
}