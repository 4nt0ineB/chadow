package fr.uge.chadow.core.protocol.server;

import fr.uge.chadow.core.protocol.Frame;
import fr.uge.chadow.core.protocol.Opcode;
import fr.uge.chadow.core.protocol.field.SocketField;

import java.nio.ByteBuffer;
import java.util.Arrays;

public record RequestOpenDownload(SocketField[] sockets) implements Frame {
  @Override
  public ByteBuffer toByteBuffer() {
    var socketsByteBuffersArray = new ByteBuffer[sockets.length];
    var bufferCapacity = 0;

    for (int i = 0; i < sockets.length; i++) {
      socketsByteBuffersArray[i] = sockets[i].toByteBuffer().flip();
      bufferCapacity += socketsByteBuffersArray[i].remaining();
    }

    var bbRequestOpenDownload = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + bufferCapacity);

    bbRequestOpenDownload.put(Opcode.REQUEST_OPEN_DOWNLOAD_RESPONSE.toByte()).putInt(sockets.length);

    Arrays.stream(socketsByteBuffersArray).forEach(bbRequestOpenDownload::put);
    return bbRequestOpenDownload;
  }
}
