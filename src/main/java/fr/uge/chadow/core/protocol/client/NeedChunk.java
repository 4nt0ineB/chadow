package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record NeedChunk(long offset, int length) implements Frame {
  
  @Override
  public ByteBuffer toByteBuffer() {
    var buffer = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + Integer.BYTES);
    return buffer.put(Opcode.toByte(this.getClass()))
                 .putLong(offset)
                 .putInt(length);
  }
}