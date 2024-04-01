package fr.uge.chadow.core.readers;

import fr.uge.chadow.core.packet.Message;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
  private enum State {
    DONE, WAITING, ERROR
  }

  private State state = State.WAITING;
  private final StringReader stringReader = new StringReader();
  private String login;
  private String txt;
  private Message value;

  @Override
  public ProcessStatus process(ByteBuffer buffer) {
    if (state == State.DONE || state == State.ERROR) {
      throw new IllegalStateException();
    }
    if (login == null) {
      var result = stringReader.process(buffer);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      login = stringReader.get();
    }
    stringReader.reset();
    if (txt == null) {
      var result = stringReader.process(buffer);
      if (result != ProcessStatus.DONE) {
        return result;
      }
      txt = stringReader.get();
    }
    state = State.DONE;
    value = new Message(login, txt, System.currentTimeMillis());
    return ProcessStatus.DONE;
  }

  @Override
  public Message get() {
    if (state != State.DONE) {
      throw new IllegalStateException();
    }
    return value;
  }

  @Override
  public void reset() {
    state = State.WAITING;
    login = null;
    txt = null;
    stringReader.reset();
  }
}
