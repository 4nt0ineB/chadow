package fr.uge.chadow.core.reader;

import java.nio.ByteBuffer;

public class ArrayByteReader implements Reader<byte[]> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final IntReader intReader = new IntReader();
  private final ByteReader byteReader = new ByteReader();

  private State state = State.WAITING;
  private int size = -1;
  private int currentIndex;
  private byte[] value;

  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }

    if (size == -1) {
      var result = intReader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      size = intReader.get();
      if (size < 0) {
        return ProcessStatus.ERROR;
      }
      value = new byte[size];
    }

    while (currentIndex != size) {
      var result = byteReader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      value[currentIndex] = byteReader.get();
      byteReader.reset();
      currentIndex++;
    }
    state = State.DONE;
    return ProcessStatus.DONE;
  }

  @Override
  public byte[] get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING;
    currentIndex = 0;
    size = -1;
    intReader.reset();
    byteReader.reset();
  }
}
