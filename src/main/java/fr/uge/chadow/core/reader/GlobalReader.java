package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.field.Codex;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;

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
        readerMap.put(long.class, new LongReader());
      } else if (type.equals(byte.class)) {
        readerMap.put(byte.class, new ByteReader());
      } else if (type.isArray()) {
        var componentType = type.getComponentType();
        if (componentType.equals(DiscoveryResponse.Username.class)) {
          readerMap.put(DiscoveryResponse.Username.class, new ArrayReader<>(DiscoveryResponse.Username.class));
        } else if (componentType.equals(Codex.FileInfo.class)) {
          readerMap.put(Codex.FileInfo.class, new ArrayReader<>(Codex.FileInfo.class));
        } else {
          throw new IllegalArgumentException(STR."Unsupported type: \{type}");
        }
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
    while (currentIndex != recordInstanceValues.length) {
      var reader = readerMap.get(recordComponents[currentIndex].getType());
      var result = reader.process(bb);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      recordInstanceValues[currentIndex] = reader.get();
      reader.reset();
      currentIndex++;
    }
    state = State.DONE;
    try {
      @SuppressWarnings("unchecked")
      T instance = (T) recordClass.getConstructors()[0].newInstance(recordInstanceValues);
      value = instance;
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
}
