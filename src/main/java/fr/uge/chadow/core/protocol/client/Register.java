package fr.uge.chadow.core.protocol.client;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.net.Socket;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public record Register(String username, int listenerPort, SocketField serverPublicAddress) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var opcode = Opcode.toByte(this.getClass());
    var bbUsername = UTF_8.encode(username);
    var socketFieldBuffer = serverPublicAddress.toByteBuffer().flip();
    var bbMsg = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bbUsername.remaining() + Integer.BYTES + socketFieldBuffer.remaining());
    return bbMsg
        .put(opcode)
        .putInt(bbUsername.remaining())
        .put(bbUsername)
        .putInt(listenerPort)
        .put(socketFieldBuffer);
  }
}