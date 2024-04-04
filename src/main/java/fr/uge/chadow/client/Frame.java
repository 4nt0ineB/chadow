package fr.uge.chadow.client;

import java.nio.ByteBuffer;

public interface Frame {
  ByteBuffer toByteBuffer();
}
