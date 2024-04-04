package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Register(String username) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.REGISTER.toByte();
    var bbUsername = UTF_8.encode(username);
    var bbMsg = ByteBuffer.allocate(Byte.BYTES + bbUsername.remaining() + Integer.BYTES);
    return bbMsg.put(opcode).putInt(bbUsername.remaining()).put(bbUsername);
  }

}
