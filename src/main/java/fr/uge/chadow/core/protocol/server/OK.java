package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record OK() implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    return ByteBuffer.allocate(Byte.BYTES).put(Opcode.toByte(this.getClass()));
  }
}