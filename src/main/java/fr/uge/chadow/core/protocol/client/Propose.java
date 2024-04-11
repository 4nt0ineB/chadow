package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.Codex;

import java.nio.ByteBuffer;

public record Propose(Codex codex) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.PROPOSE.toByte();
    var codexBuffer = codex.toByteBuffer().flip();
    var buffer = ByteBuffer.allocate(Byte.BYTES + codexBuffer.remaining());
    return buffer.put(opcode).put(codexBuffer);
  }
}
