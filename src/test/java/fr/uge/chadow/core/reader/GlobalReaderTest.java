package fr.uge.chadow.core.reader;

import fr.uge.chadow.client.CodexController;
import fr.uge.chadow.core.protocol.YellMessage;
import fr.uge.chadow.core.protocol.TestPacket;
import fr.uge.chadow.core.protocol.client.Propose;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GlobalReaderTest {
  @Test
  public void simple() {
    var bb = ByteBuffer.allocate(100);
    var reader = new GlobalReader<>(YellMessage.class);
    var login = "username";
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
    assertEquals(new YellMessage(login, txt, messageTime), reader.get());
  }

  @Test
  public void smallBuffer() {
    var bb = ByteBuffer.allocate(1024);
    var reader = new GlobalReader<>(YellMessage.class);
    var login = "username";
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
    assertEquals(new YellMessage(login, txt, messageTime), reader.get());
  }

  @Test
  public void errorGet() {
    var reader = new GlobalReader<>(YellMessage.class);
    assertThrows(IllegalStateException.class, () -> {
      var res = reader.get();
    });
  }

  @Test
  public void errorNeg() {
    var bb = ByteBuffer.allocate(100);
    var reader = new GlobalReader<>(YellMessage.class);
    var login = "username";
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
  
  @Test
  public void proposeCodex() {
    var codexController = new CodexController();
    CodexController.CodexStatus codexStatus;
    try {
      codexStatus = codexController.createFromPath("my codex", "/home/alan1/Pictures");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    //System.out.println(codexStatus.codex().id());
    var propose = new Propose(codexStatus.codex());
    var reader = new GlobalReader<>(Propose.class);
    var bb = propose.toByteBuffer();
    bb.flip();
    bb.get(); // Skip opcode
    bb.compact();
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    System.out.println(reader.get().codex());
  }
}