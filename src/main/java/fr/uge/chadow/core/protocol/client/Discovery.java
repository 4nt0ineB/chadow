package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

public record Discovery() implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(1);
    bb.put(Opcode.toByte(this.getClass()));
    return bb;
  }
}