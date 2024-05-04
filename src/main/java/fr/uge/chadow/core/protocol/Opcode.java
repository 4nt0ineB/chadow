package fr.uge.chadow.core.protocol;

import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.client.Propose;
import fr.uge.chadow.core.protocol.client.Request;
import fr.uge.chadow.core.protocol.server.*;
import fr.uge.chadow.core.protocol.client.Register;
import fr.uge.chadow.core.reader.GlobalReader;

import java.util.HashMap;
import java.util.Optional;

public enum Opcode {
  // ERROR
  REGISTER(Register.class),
  OK(OK.class),
  DISCOVERY(Discovery.class),
  DISCOVERY_RESPONSE(DiscoveryResponse.class),
  EVENT(Event.class),
  YELL(YellMessage.class),
  WHISPER(WhisperMessage.class),
  PROPOSE(Propose.class),
  REQUEST(Request.class),
  REQUEST_RESPONSE(RequestResponse.class),
  SEARCH(Search.class),
  SEARCH_RESPONSE(SearchResponse.class),
  REQUEST_DOWNLOAD(RequestDownload.class),
  REQUEST_OPEN_DOWNLOAD_RESPONSE(RequestOpenDownload.class),
  REQUEST_CLOSED_DOWNLOAD_RESPONSE(ClosedDownloadResponse.class),
  HANDSHAKE(Handshake.class),
  DENIED(Denied.class),
  NEEDCHUNK(NeedChunk.class),
  //CANCEL (Cancel.class),
  HERECHUNK(HereChunk.class),
  PROXY(Proxy.class),
  PROXYOPEN(ProxyOpen.class),
  PROXYOK(ProxyOk.class),
  HIDDEN(Hidden.class),
  UPDATE(Update.class),
  ;

  private static final HashMap<Class<? extends Record>, Opcode> classMap = new HashMap<>();

  static {
    for (var opcode : values()) {
      classMap.put(opcode.recordClass, opcode);
    }
  }

  private final Class<? extends Record> recordClass;

  <T extends Record & Frame> Opcode(Class<T> recordClass) {
    this.recordClass = recordClass;
  }

  /**
   * Method that get the GlobalReader
   *
   * @return the GlobalReader of the associated Frame
   */
  public GlobalReader<? extends Frame> getReader() {
    @SuppressWarnings("unchecked")
    var reader = (GlobalReader<? extends Frame>) new GlobalReader<>(recordClass);
    return reader;
  }

  /**
   * Method that get the Opcode from a Frame record
   *
   * @param recordClass the Frame record class
   * @param <T>         Frame record
   * @return the Opcode of the Frame record
   */
  public static <T extends Record & Frame> byte toByte(Class<T> recordClass) {
    return Optional.ofNullable(classMap.get(recordClass))
            .map(Opcode::toByte)
            .orElseThrow(() -> new IllegalArgumentException("Unknown Frame record"));
  }

  public byte toByte() {
    return (byte) this.ordinal();
  }
}