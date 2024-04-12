package fr.uge.chadow.core.reader;


import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.Discovery;
import fr.uge.chadow.core.protocol.client.Propose;
import fr.uge.chadow.core.protocol.client.Register;
import fr.uge.chadow.core.protocol.client.Request;
import fr.uge.chadow.core.protocol.server.DiscoveryResponse;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.OK;
import fr.uge.chadow.core.protocol.server.RequestResponse;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Logger;

public class FrameReader implements Reader<Frame> {
  private static final Logger logger = Logger.getLogger(FrameReader.class.getName());
  private enum State {
    DONE, WAITING, ERROR
  }

  private final ByteReader byteReader = new ByteReader();
  private final Map<Opcode, GlobalReader<? extends Frame>> readers = Map.of(
          Opcode.REGISTER, new GlobalReader<>(Register.class),
          Opcode.YELL, new GlobalReader<>(YellMessage.class),
          Opcode.WHISPER, new GlobalReader<>(WhisperMessage.class),
          Opcode.OK, new GlobalReader<>(OK.class),
          Opcode.DISCOVERY, new GlobalReader<>(Discovery.class),
          Opcode.PROPOSE, new GlobalReader<>(Propose.class),
          Opcode.REQUEST, new GlobalReader<>(Request.class),
          Opcode.DISCOVERY_RESPONSE, new GlobalReader<>(DiscoveryResponse.class),
          Opcode.EVENT, new GlobalReader<>(Event.class),
          Opcode.REQUEST_RESPONSE, new GlobalReader<>(RequestResponse.class)
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
  
    logger.info("Opcode: " + opcode);
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