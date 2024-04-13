package fr.uge.chadow.core.reader;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;

public class ArrayReader<T> implements Reader<T[]> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final IntReader intReader = new IntReader();
  private final Reader<T> elementReader;
  private final Class<T> elementClass;

  private State state = State.WAITING;
  private int size = -1;
  private int currentIndex;
  private T[] value;

  public ArrayReader(Reader<T> elementReader, Class<T> elementClass) {
    this.elementReader = elementReader;
    this.elementClass = elementClass;
  }

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
      @SuppressWarnings("unchecked")
      T[] array = (T[]) Array.newInstance(elementClass, size);
      value = array;
    }

    while (currentIndex != size) {
      var result = elementReader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      value[currentIndex] = elementReader.get();
      elementReader.reset();
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
    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING;
    size = -1;
    currentIndex = 0;
    intReader.reset();
    elementReader.reset();
  }
}
