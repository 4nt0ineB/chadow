package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Register(String username, SocketField socketField) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.REGISTER.toByte();
    var bbUsername = UTF_8.encode(username);
    var bbSocketField = socketField.toByteBuffer().flip();
    var bbMsg = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbUsername.remaining() + bbSocketField.remaining());
    return bbMsg.put(opcode).putInt(bbUsername.remaining()).put(bbUsername).put(bbSocketField);
  }

}
