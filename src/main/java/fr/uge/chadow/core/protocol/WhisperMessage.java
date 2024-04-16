package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record WhisperMessage(String username, String txt, long epoch) implements Frame {

  public ByteBuffer toByteBuffer() {
    byte opcode = Opcode.toByte(this.getClass());
    var bbLogin = UTF_8.encode(username);
    var bbTxt = UTF_8.encode(txt);
    var bbMsg = ByteBuffer.allocate(bbLogin.remaining() + bbTxt.remaining() + 2 * Integer.BYTES + Long.BYTES + Byte.BYTES);

    return bbMsg.put(opcode).putInt(bbLogin.remaining()).put(bbLogin).putInt(bbTxt.remaining()).put(bbTxt).putLong(epoch);
  }
}