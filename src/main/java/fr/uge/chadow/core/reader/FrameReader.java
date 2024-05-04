package fr.uge.chadow.core.reader;


import fr.uge.chadow.core.protocol.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class FrameReader implements Reader<Frame> {
  private static final Logger logger = Logger.getLogger(FrameReader.class.getName());

  private enum State {
    DONE, WAITING, ERROR
  }

  private final ByteReader byteReader = new ByteReader();
  private final Map<Opcode, GlobalReader<? extends Frame>> readers = new HashMap<>();

  private State state = State.WAITING;
  private Opcode opcode;
  private Frame frame;

  public FrameReader() {
    for(var opcode : Opcode.values()) {
      readers.put(opcode, opcode.getReader());
    }
  }

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
      opcode = Opcode.values()[byteReader.get()];
      logger.info(STR."Received opcode: \{opcode}");
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
    byteReader.reset();
    for (var reader : readers.values()) {
      reader.reset();
    }
  }
}