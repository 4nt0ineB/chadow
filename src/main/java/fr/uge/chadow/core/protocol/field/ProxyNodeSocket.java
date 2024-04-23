package fr.uge.chadow.core.protocol.field;

public record ProxyNodeSocket(SocketField socket, int chainId) {
    public ProxyNodeSocket {
      if (chainId < 0) {
        throw new IllegalArgumentException("chainId must be positive");
      }
    }
  }