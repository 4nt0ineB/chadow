package fr.uge.chadow.core.protocol;

public enum Opcode {
  ERROR,
  REGISTER,
  OK,
  DISCOVERY,
  DISCOVERY_RESPONSE,
  EVENT,
  YELL,
  WHISPER,
  PROPOSE,
  REQUEST,
  REQUEST_RESPONSE,
  SEARCH,
  SEARCH_RESPONSE,
  REQUEST_DOWNLOAD,
  REQUEST_OPEN_DOWNLOAD_RESPONSE,
  REQUEST_CLOSED_DOWNLOAD_RESPONSE,
  HANDSHAKE,
  DENIED,
  NEEDCHUNK,
  CANCEL,
  HERECHUNK,
  PROXY,
  PROXYOPEN,
  PROXYOK,
  HIDDEN,
  ;

  public static Opcode from(byte value) {
    return switch (value) {
        case 0 -> ERROR;
        case 1 -> REGISTER;
        case 2 -> OK;
        case 3 -> DISCOVERY;
        case 4 -> DISCOVERY_RESPONSE;
        case 5 -> EVENT;
        case 6 -> YELL;
        case 7 -> WHISPER;
        case 8 -> PROPOSE;
        case 9 -> REQUEST;
        case 10 -> REQUEST_RESPONSE;
        case 11 -> SEARCH;
        case 12 -> SEARCH_RESPONSE;
        case 13 -> REQUEST_DOWNLOAD;
        case 14 -> REQUEST_OPEN_DOWNLOAD_RESPONSE;
        case 15 -> REQUEST_CLOSED_DOWNLOAD_RESPONSE;
        case 16 -> HANDSHAKE;
        case 17 -> DENIED;
        case 18 -> NEEDCHUNK;
        case 19 -> CANCEL;
        case 20 -> HERECHUNK;
        case 21 -> PROXY;
        case 22 -> PROXYOPEN;
        case 23 -> PROXYOK;
        case 24 -> HIDDEN;
        default -> throw new IllegalArgumentException(STR."Invalid value: \{value}");
    };
  }

  public byte toByte() {
    return (byte) this.ordinal();
  }
}