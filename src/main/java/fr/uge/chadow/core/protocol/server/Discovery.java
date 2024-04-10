package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Discovery(Username[] usernames) implements Frame {
  public record Username(String username) {
    public ByteBuffer toByteBuffer() {
      var bbUsername = UTF_8.encode(username);
      var bbMsg = ByteBuffer.allocate(bbUsername.remaining() + Integer.BYTES);
      return bbMsg.putInt(bbUsername.remaining()).put(bbUsername);
    }
  }

  @Override
  public ByteBuffer toByteBuffer() {
    var usernamesByteBuffersArray = new ByteBuffer[usernames.length];
    for (int i = 0; i < usernames.length; i++) {
      usernamesByteBuffersArray[i] = usernames[i].toByteBuffer();
    }

    int bufferCapacity = 0;
    for (var usernameByteBuffer : usernamesByteBuffersArray) {
      bufferCapacity += usernameByteBuffer.remaining();
    }

    var bbDiscovery = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bufferCapacity);

    bbDiscovery.put(Opcode.DISCOVERY.toByte()).putInt(usernames.length);
    Arrays.stream(usernamesByteBuffersArray).forEach(usernameByteBuffer -> {
      usernameByteBuffer.flip();
      bbDiscovery.put(usernameByteBuffer);
    });
    return bbDiscovery;
  }
}
