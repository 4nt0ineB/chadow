package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.Codex;

import java.nio.ByteBuffer;

public record RequestResponse(Codex codex) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.toByte(this.getClass());
    var codexIdBuffer = codex.toByteBuffer()
                             .flip();
    var bb = ByteBuffer.allocate(Byte.BYTES + codexIdBuffer.remaining());
    return bb.put(opcode)
             .put(codexIdBuffer);
  }
}