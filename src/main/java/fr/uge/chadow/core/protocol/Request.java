package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

public record Request(String sha1) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(1 + Integer.BYTES + sha1.length());
    bb.put(Opcode.REQUEST.toByte());
    bb.putInt(sha1.length());
    bb.put(sha1.getBytes());
    return bb;
  }
}