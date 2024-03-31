package fr.uge.chadow.core;

import java.nio.ByteBuffer;

public class GlobalReader<T extends Record> implements Reader<T> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private State state = State.WAITING;

  private T value;

  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    return null;
  }

  @Override
  public T get() {

    return null;
  }

  @Override
  public void reset() {

  }

  public static void main(String[] args) {

  }
}
