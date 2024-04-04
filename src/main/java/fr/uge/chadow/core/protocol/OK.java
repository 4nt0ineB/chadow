package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

public record OK() implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    return ByteBuffer.allocate(Byte.BYTES).put(Opcode.OK.toByte());
  }
}
