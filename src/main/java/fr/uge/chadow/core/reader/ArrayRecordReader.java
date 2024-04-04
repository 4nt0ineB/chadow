package fr.uge.chadow.core.reader;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ArrayRecordReader<T extends Record> implements Reader<T[]> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final GlobalReader<T> reader;
  private final int size;
  private final T[] value;

  private State state = State.WAITING;
  private int currentIndex;

  @SuppressWarnings("unchecked")
  public ArrayRecordReader(Class<T> clazz, int size) {
    this.reader = new GlobalReader<>(clazz);
    this.size = size;
    this.value = (T[]) new Object[size];
  }

  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    while (currentIndex != size) {
      var result = reader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      value[currentIndex] = reader.get();
      reader.reset();
      currentIndex++;
    }
    state = State.DONE;
    return ProcessStatus.DONE;
  }

  @Override
  public T[] get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return Arrays.copyOf(value, size);
  }

  @Override
  public void reset() {
    state = State.WAITING;
    currentIndex = 0;
    reader.reset();
  }


}
