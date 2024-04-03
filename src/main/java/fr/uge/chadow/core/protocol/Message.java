package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record Message(String login, String txt, long epoch) {
  private static final Charset UTF_8 = StandardCharsets.UTF_8;

  public ByteBuffer toByteBuffer() {
    var bbLogin = UTF_8.encode(login);
    var bbTxt = UTF_8.encode(txt);
    var bbMsg = ByteBuffer.allocate(bbLogin.remaining() + bbTxt.remaining() + 2 * Integer.BYTES + Long.BYTES);

    return bbMsg.putInt(bbLogin.remaining()).put(bbLogin).putInt(bbTxt.remaining()).put(bbTxt).putLong(epoch);
  }
}