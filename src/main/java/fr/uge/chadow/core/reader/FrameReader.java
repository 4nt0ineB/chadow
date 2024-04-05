package fr.uge.chadow.core.reader;

import fr.uge.chadow.core.protocol.*;

import java.nio.ByteBuffer;
import java.util.Map;

public class FrameReader implements Reader<Frame> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private final ByteReader byteReader = new ByteReader();
  private final Map<Opcode, GlobalReader<? extends Frame>> readers = Map.of(
          Opcode.REGISTER, new GlobalReader<>(Register.class),
          Opcode.YELL, new GlobalReader<>(YellMessage.class),
          Opcode.WHISPER, new GlobalReader<>(WhisperMessage.class)
  );

  private State state = State.WAITING;
  private Opcode opcode;
  private Frame frame;

  @Override
  public ProcessStatus process(ByteBuffer bb) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }

    if (opcode == null) {
      ProcessStatus opcodeStatus = byteReader.process(bb);
      if (opcodeStatus != ProcessStatus.DONE) {
        return opcodeStatus;
      }
      opcode = Opcode.from(byteReader.get());
      if (!readers.containsKey(opcode)) {
        return ProcessStatus.ERROR;
      }
    }

    ProcessStatus frameStatus = readers.get(opcode).process(bb);
    if (frameStatus != ProcessStatus.DONE) {
      return frameStatus;
    }

    frame = readers.get(opcode).get();
    state = State.DONE;
    return ProcessStatus.DONE;
  }

  @Override
  public Frame get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return frame;
  }

  @Override
  public void reset() {
    state = State.WAITING;
    opcode = null;
    frame = null;
    for (var reader : readers.values()) {
      reader.reset();
    }
  }
}
