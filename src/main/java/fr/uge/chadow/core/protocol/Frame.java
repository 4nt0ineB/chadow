package fr.uge.chadow.core.protocol;

import java.nio.ByteBuffer;

public interface Frame {
  ByteBuffer toByteBuffer();
}
