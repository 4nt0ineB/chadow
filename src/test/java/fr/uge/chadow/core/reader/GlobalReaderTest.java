package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.Message;
import fr.uge.chadow.core.protocol.TestPacket;
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
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    assertEquals(new Message(login, txt, messageTime), reader.get());
  }

  @Test
  public void smallBuffer() {
    var bb = ByteBuffer.allocate(1024);
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
    var smallBuffer = ByteBuffer.allocate(10);
    while (bb.hasRemaining()) {
      while (smallBuffer.hasRemaining() && bb.hasRemaining()) {
        smallBuffer.put(bb.get());
      }
      if (bb.hasRemaining()) {
        assertEquals(Reader.ProcessStatus.REFILL, reader.process(smallBuffer));
      } else {
        assertEquals(Reader.ProcessStatus.DONE, reader.process(smallBuffer));
      }
    }
    assertEquals(new Message(login, txt, messageTime), reader.get());
  }

  @Test
  public void errorGet() {
    var reader = new GlobalReader<>(Message.class);
    assertThrows(IllegalStateException.class, () -> {
      var res = reader.get();
    });
  }

  @Test
  public void errorNeg() {
    var bb = ByteBuffer.allocate(100);
    var reader = new GlobalReader<>(Message.class);
    var login = "login";
    var txt = "First test of the GlobalReader";
    long messageTime = System.currentTimeMillis();
    var loginBuffer = StandardCharsets.UTF_8.encode(login);
    var txtBuffer = StandardCharsets.UTF_8.encode(txt);
    bb.putInt(-1);
    bb.put(loginBuffer);
    bb.putInt(txtBuffer.remaining());
    bb.put(txtBuffer);
    bb.putLong(messageTime);
    assertEquals(Reader.ProcessStatus.ERROR, reader.process(bb));
  }

  @Test
  public void simpleForTestRecord() {
    var bb = ByteBuffer.allocate(1024);
    var reader = new GlobalReader<>(TestPacket.class);
    String testString = "testString";
    int testInt = 42;
    long testLong = 4242424242424242L;
    String testString2 = "testString2";
    var testStringBuffer = StandardCharsets.UTF_8.encode(testString);
    var testString2Buffer = StandardCharsets.UTF_8.encode(testString2);
    bb.putInt(testStringBuffer.remaining());
    bb.put(testStringBuffer);
    bb.putInt(testInt);
    bb.putLong(testLong);
    bb.putInt(testString2Buffer.remaining());
    bb.put(testString2Buffer);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    assertEquals(new TestPacket(testString, testInt, testLong, testString2), reader.get());
  }

  @Test
  public void simpleForTestRecordWithSmallBuffer() {
    var bb = ByteBuffer.allocate(1024);
    var reader = new GlobalReader<>(TestPacket.class);
    String testString = "testString";
    int testInt = 42;
    long testLong = 4242424242424242L;
    String testString2 = "testString2";
    var testStringBuffer = StandardCharsets.UTF_8.encode(testString);
    var testString2Buffer = StandardCharsets.UTF_8.encode(testString2);
    bb.putInt(testStringBuffer.remaining());
    bb.put(testStringBuffer);
    bb.putInt(testInt);
    bb.putLong(testLong);
    bb.putInt(testString2Buffer.remaining());
    bb.put(testString2Buffer);
    bb.flip();
    var smallBuffer = ByteBuffer.allocate(10);
    while (bb.hasRemaining()) {
      while (smallBuffer.hasRemaining() && bb.hasRemaining()) {
        smallBuffer.put(bb.get());
      }
      if (bb.hasRemaining()) {
        assertEquals(Reader.ProcessStatus.REFILL, reader.process(smallBuffer));
      } else {
        assertEquals(Reader.ProcessStatus.DONE, reader.process(smallBuffer));
      }
    }
    assertEquals(new TestPacket(testString, testInt, testLong, testString2), reader.get());
  }
}
