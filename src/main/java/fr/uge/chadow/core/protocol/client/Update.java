package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Update(String codexId) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcodeByte = Opcode.toByte(this.getClass());
    var bbCodexId = UTF_8.encode(codexId);
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbCodexId.remaining());
    return bb.put(opcodeByte)
             .putInt(bbCodexId.remaining())
             .put(bbCodexId);
  }
}