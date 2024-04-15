package fr.uge.chadow.core.reader;


import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.server.*;

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
    readers.put(Opcode.REGISTER, new GlobalReader<>(Register.class));
    readers.put(Opcode.OK, new GlobalReader<>(OK.class));
    readers.put(Opcode.DISCOVERY, new GlobalReader<>(Discovery.class));
    readers.put(Opcode.DISCOVERY_RESPONSE, new GlobalReader<>(DiscoveryResponse.class));
    readers.put(Opcode.EVENT, new GlobalReader<>(Event.class));
    readers.put(Opcode.YELL, new GlobalReader<>(YellMessage.class));
    readers.put(Opcode.WHISPER, new GlobalReader<>(WhisperMessage.class));
    readers.put(Opcode.PROPOSE, new GlobalReader<>(Propose.class));
    readers.put(Opcode.REQUEST, new GlobalReader<>(Request.class));
    readers.put(Opcode.NEEDCHUNK, new GlobalReader<>(NeedChunk.class));
    readers.put(Opcode.HERECHUNK, new GlobalReader<>(HereChunk.class));
    readers.put(Opcode.DENIED, new GlobalReader<>(Denied.class));
    readers.put(Opcode.HANDSHAKE, new GlobalReader<>(Handshake.class));
    readers.put(Opcode.REQUEST_RESPONSE, new GlobalReader<>(RequestResponse.class));
    readers.put(Opcode.REQUEST_DOWNLOAD, new GlobalReader<>(RequestDownload.class));
    readers.put(Opcode.REQUEST_OPEN_DOWNLOAD_RESPONSE, new GlobalReader<>(RequestOpenDownload.class));
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
      opcode = Opcode.from(byteReader.get());
      if (!readers.containsKey(opcode)) {
        return ProcessStatus.ERROR;
      }
    }

    
    ProcessStatus frameStatus = readers.get(opcode).process(bb);
    if (frameStatus != ProcessStatus.DONE) {
      logger.info(STR."Opcode: \{opcode}");
      logger.info(STR."Frame status: \{frameStatus}");
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