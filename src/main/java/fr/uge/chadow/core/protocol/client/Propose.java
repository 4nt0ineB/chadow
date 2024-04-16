package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.Codex;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

public record Propose(Codex codex) implements Frame {
  private static final Logger logger = Logger.getLogger(Propose.class.getName());
  
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.toByte(this.getClass());
    var codexBuffer = codex.toByteBuffer().flip();
    var buffer = ByteBuffer.allocate(Byte.BYTES + codexBuffer.remaining());
    logger.info(STR."Propose frame created of size \{buffer.remaining()} bytes");
    return buffer.put(opcode).put(codexBuffer);
  }
}