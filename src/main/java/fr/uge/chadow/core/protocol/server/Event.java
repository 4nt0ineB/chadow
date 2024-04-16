package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Event(byte code, String username) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.toByte(this.getClass());
    var bbUsername = UTF_8.encode(username);
    var bb = ByteBuffer.allocate(Byte.BYTES + Byte.BYTES + Integer.BYTES + bbUsername.remaining());
    return bb.put(opcode).put(code).putInt(bbUsername.remaining()).put(bbUsername);
  }
}