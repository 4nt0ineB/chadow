package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record YellMessage(String login, String txt, long epoch) implements Frame {

  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.YELL.toByte();
    var bbLogin = UTF_8.encode(login);
    var bbTxt = UTF_8.encode(txt);
    var bbMsg = ByteBuffer.allocate(Byte.BYTES + bbLogin.remaining() + bbTxt.remaining() + 2 * Integer.BYTES +
            Long.BYTES);

    return bbMsg.put(opcode).putInt(bbLogin.remaining()).put(bbLogin).putInt(bbTxt.remaining())
            .put(bbTxt).putLong(epoch);
  }
}