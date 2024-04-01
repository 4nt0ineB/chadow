package fr.uge.chadow.core;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GlobalReaderTest {
  @Test
  public void simple() {
    var bb = ByteBuffer.allocate(100);
    var reader = new GlobalReader<>(Message.class);
    var login = "login";
    var txt = "First test of the GlobalReader";
    long messageTime = System.currentTimeMillis();
    var loginBuffer = StandardCharsets.UTF_8.encode(login);
    var txtBuffer = StandardCharsets.UTF_8.encode(txt);
    bb.putInt(loginBuffer.remaining());
    bb.put(loginBuffer);
    bb.putInt(txtBuffer.remaining());
    bb.put(txtBuffer);
    bb.putLong(messageTime);
    bb.flip();
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    assertEquals(new Message(login, txt, messageTime), reader.get());
  }
}
