package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

public record DiscoveryResponse(String[] usernames) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var usernamesByteBuffersArray = new ByteBuffer[usernames.length];
    int bufferCapacity = 0;
    for (int i = 0; i < usernames.length; i++) {
      usernamesByteBuffersArray[i] = UTF_8.encode(usernames[i]);
      bufferCapacity += Integer.BYTES + usernamesByteBuffersArray[i].remaining();
    }

    var bbDiscovery = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bufferCapacity);

    bbDiscovery.put(Opcode.toByte(this.getClass())).putInt(usernames.length);
    Arrays.stream(usernamesByteBuffersArray).forEach(bbUsername -> bbDiscovery.putInt(bbUsername.remaining()).put(bbUsername));
    return bbDiscovery;
  }
}