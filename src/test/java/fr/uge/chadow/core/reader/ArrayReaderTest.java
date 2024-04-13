package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import fr.uge.chadow.core.protocol.server.Event;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArrayReaderTest {
  @Test
  public void simple() {
    DiscoveryResponse.Username[] usernames = new DiscoveryResponse.Username[]{new DiscoveryResponse.Username("username1"), new DiscoveryResponse.Username("username2"), new DiscoveryResponse.Username("username3")};
    DiscoveryResponse discoveryResponse = new DiscoveryResponse(usernames);
    var bb = discoveryResponse.toByteBuffer();
    bb.flip();
    bb.get(); // Skip opcode
    bb.compact();
    var reader = new ArrayReader<>(new GlobalReader<>(DiscoveryResponse.Username.class), DiscoveryResponse.Username.class);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.stream(usernames).toList(), Arrays.stream(res).toList());
  }

  @Test
  public void simple2() {
    String[] strings = new String[]{"string1", "string2", "string3"};
    ByteBuffer[] byteBuffers = new ByteBuffer[strings.length];
    int totalSize = 0;
    for (int i = 0; i < strings.length; i++) {
      byteBuffers[i] = UTF_8.encode(strings[i]);
      totalSize += byteBuffers[i].remaining();
    }
    ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES * strings.length + totalSize);
    bb.putInt(strings.length);
    for (int i = 0; i < strings.length; i++) {
      bb.putInt(byteBuffers[i].remaining());
      bb.put(byteBuffers[i]);
    }
    var reader = new ArrayReader<>(new StringReader(), String.class);

    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.stream(strings).toList(), Arrays.stream(res).toList());
  }

  @Test
  public void simple3() {
    byte[] bytes = new byte[]{1, 2, 3, 4, 5};
    ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES + bytes.length);
    bb.putInt(bytes.length);
    bb.put(bytes);
    var reader = new ArrayReader<>(new ByteReader(), Byte.class);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.toString(bytes), Arrays.toString(res));
  }

  @Test
  public void simple4() {
    int[] ints = new int[]{1, 2, 3, 4, 5};
    ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES * ints.length);
    bb.putInt(ints.length);
    for (int i : ints) {
      bb.putInt(i);
    }
    var reader = new ArrayReader<>(new IntReader(), Integer.class);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.toString(ints), Arrays.toString(res));
  }

  @Test
  public void simple5() {
    Event[] events = new Event[]{new Event((byte) 1, "username1"), new Event((byte) 2, "username2"), new Event((byte) 3, "username3")};
    ByteBuffer[] byteBuffers = new ByteBuffer[events.length];
    int totalSize = 0;
    for (int i = 0; i < events.length; i++) {
      byteBuffers[i] = events[i].toByteBuffer().flip();
      byteBuffers[i].get(); // Skip opcode
      byteBuffers[i].compact();
      totalSize += byteBuffers[i].flip().remaining();
    }
    ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES + totalSize);
    bb.putInt(events.length);
    for (int i = 0; i < events.length; i++) {
      bb.put(byteBuffers[i]);
    }

    var reader = new ArrayReader<>(new GlobalReader<>(Event.class), Event.class);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.stream(events).toList(), Arrays.stream(res).toList());
  }
}
