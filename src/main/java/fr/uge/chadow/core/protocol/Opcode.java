package fr.uge.chadow.core.protocol;

public enum Opcode {
  ERROR,
  REGISTER,
  DISCOVERY,
  EVENT,
  YELL,
  WHISPER,
  PROPOSE,
  ANNOUNCE,
  SEARCH,
  REQUEST,
  HANDSHAKE,
  NEEDCHUNK,
  CANCEL,
  HERECHUNK,
  DENIED,
  PROXY,
  PROXYOPEN,
  PROXYOK,
  HIDDEN,
  SHARING,
  STOPSHARING,
  OK;

  public static Opcode from(byte value) {
    return switch (value) {
      case 0 -> ERROR;
      case 1 -> REGISTER;
      case 2 -> DISCOVERY;
      case 3 -> EVENT;
      case 4 -> YELL;
      case 5 -> WHISPER;
      case 6 -> PROPOSE;
      case 7 -> ANNOUNCE;
      case 8 -> SEARCH;
      case 9 -> REQUEST;
      case 10 -> HANDSHAKE;
      case 11 -> NEEDCHUNK;
      case 12 -> CANCEL;
      case 13 -> HERECHUNK;
      case 14 -> DENIED;
      case 15 -> PROXY;
      case 16 -> PROXYOPEN;
      case 17 -> PROXYOK;
      case 18 -> HIDDEN;
      case 19 -> SHARING;
      case 20 -> STOPSHARING;
      case 21 -> OK;
      default -> throw new IllegalArgumentException("Invalid value for Opcode");
    };
  }
}
