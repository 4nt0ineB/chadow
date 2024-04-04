package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.MyArray;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ArrayReader<T extends Record> implements Reader<T[]> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final IntReader intReader = new IntReader();
  //private final GlobalReader<T> reader;

  private State state = State.WAITING;
  private int size = -1;
  private int currentIndex;
  private T[] value;

  public ArrayReader(Class<T> recordClass) {
    var recordComponents = recordClass.getRecordComponents();
    var type = recordComponents[1].getType().getComponentType();
    //reader = new GlobalReader<>(type);
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
      value = (T[]) new Object[size];
    }

//    while (currentIndex != size) {
//      var result = reader.process(bb);
//      if (result != ProcessStatus.DONE) {
//        return result;
//      }
//      value[currentIndex] = reader.get();
//      reader.reset();
//      currentIndex++;
//    }
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
    size = -1;
    currentIndex = 0;
    intReader.reset();
    //reader.reset();
  }
}
