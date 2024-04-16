package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Request(String codexId) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.toByte(this.getClass());
    var codexIdBuffer = UTF_8.encode(codexId);
    var bb = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + codexIdBuffer.remaining());
    return bb.put(opcode).putInt(codexIdBuffer.remaining()).put(codexIdBuffer);
  }
}