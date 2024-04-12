package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.YellMessage;
import fr.uge.chadow.core.protocol.TestPacket;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArrayReaderTest {
  @Test
  public void simple() {
    DiscoveryResponse.Username[] usernames = new DiscoveryResponse.Username[] {
      new DiscoveryResponse.Username("username1"),
      new DiscoveryResponse.Username("username2"),
      new DiscoveryResponse.Username("username3")
    };
    DiscoveryResponse discoveryResponse = new DiscoveryResponse(usernames);
    var bb = discoveryResponse.toByteBuffer();
    bb.flip();
    bb.get(); // Skip opcode
    bb.compact();
    var reader = new ArrayReader<>(DiscoveryResponse.Username.class);
    assertEquals(Reader.ProcessStatus.DONE, reader.process(bb));
    var res = reader.get();
    assertEquals(Arrays.stream(usernames).toList(), Arrays.stream(res).toList());
  }
}
