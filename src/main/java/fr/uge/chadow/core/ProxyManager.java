package fr.uge.chadow.core;

import fr.uge.chadow.core.protocol.field.SocketField;

import java.util.HashMap;
import java.util.Optional;

public class ProxyManager {
  // Proxy
  private final HashMap<Integer, SocketField> proxyRoutes = new HashMap<>();
  
  public boolean saveProxyRoute(int chainId, SocketField socket) {
    return proxyRoutes.putIfAbsent(chainId, socket) == null;
  }
  
  public Optional<SocketField> getNextHopSocket(int chainId) {
    return Optional.ofNullable(proxyRoutes.get(chainId));
  }
}