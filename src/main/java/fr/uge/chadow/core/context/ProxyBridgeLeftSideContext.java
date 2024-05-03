package fr.uge.chadow.core.context;

import fr.uge.chadow.core.protocol.Frame;

/**
 * Represent the Proxy left bridge side context.
 * <pre>
 *
 *   | Left side                                | Right side                        |
 *   |----------------------------------------- |-----------------------------------|
 *   | ClientAsServerContext (acting as Proxy)  |                                   |
 *   | ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~<--|---> ProxyBridgeRightSideContext <-|---> Next Hop (Proxy/Sharer)
 *   | ServerContext (acting as Proxy)          |                                   |
 *   |------------------------------------------|-----------------------------------|
 * </pre>
 */
public sealed interface ProxyBridgeLeftSideContext permits ClientAsServerContext, ServerContext {
  void setBridge(Context bridge);
  void queueFrame(Frame frame);
  void silentlyClose();
}