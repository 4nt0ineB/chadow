package fr.uge.chadow.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class GlobalReader<T extends Record> implements Reader<T> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final Class<T> recordClass;
  private final RecordComponent[] recordComponents;
  private final Object[] recordInstanceValues;
  private final Map<Class<?>, Reader<?>> readerMap = new HashMap<>();
  private State state = State.WAITING;
  private int currentIndex;
  private T value;

  public GlobalReader(Class<T> recordClass) {
    this.recordClass = recordClass;
    this.recordComponents = recordClass.getRecordComponents();
    this.recordInstanceValues = new Object[recordComponents.length];

    for (var component : recordComponents) {
      var type = component.getType();
      if (type.equals(String.class)) {
        readerMap.put(String.class, new StringReader());
      } else if (type.equals(int.class)) {
        readerMap.put(int.class, new IntReader());
      } else if (type.equals(long.class)) {
        // TODO: add LongReader
      } else {
        throw new IllegalArgumentException(STR."Unsupported type: \{type}");
      }
    }
  }

  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    if (currentIndex != recordInstanceValues.length) {
      var reader = readerMap.get(recordComponents[currentIndex].getType());
      var result = reader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      recordInstanceValues[currentIndex] = reader.get();
      reader.reset();
      currentIndex++;
      return ProcessStatus.REFILL;
    }
    state = State.DONE;
    try {
      value = (T) recordClass.getConstructors()[0].newInstance(recordInstanceValues);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
    return ProcessStatus.DONE;
  }

  @Override
  public T get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING;
    currentIndex = 0;
    for (var reader : readerMap.values()) {
      reader.reset();
    }
  }

  public static void main(String[] args) throws InvocationTargetException, InstantiationException, IllegalAccessException {
    GlobalReader<Message> reader = new GlobalReader<>(Message.class);
    /*this.recordInstanceValues[0] = "login";
    this.recordInstanceValues[1] = "txt";
    this.recordInstanceValues[2] = 3;
    System.out.println(recordClass.getConstructors()[0].newInstance(recordInstanceValues));*/
  }
}
