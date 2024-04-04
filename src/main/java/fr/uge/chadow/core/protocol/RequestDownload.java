package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

public record RequestDownload(String codexId, byte mode) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var bb = ByteBuffer.allocate(1 + Integer.BYTES + codexId.length() + Byte.BYTES);
    bb.put(Opcode.REQUEST.toByte());
    bb.putInt(codexId.length());
    bb.put(codexId.getBytes());
    bb.put(mode);
    return bb;
  }
}