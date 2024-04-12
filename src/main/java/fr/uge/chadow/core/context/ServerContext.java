package fr.uge.chadow.core.context;

import fr.uge.chadow.core.protocol.*;
import fr.uge.chadow.core.protocol.client.*;
import fr.uge.chadow.core.protocol.server.Event;
import fr.uge.chadow.core.protocol.server.OK;
import fr.uge.chadow.server.Server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

public final class ServerContext extends Context {
  private static final Logger logger = Logger.getLogger(Server.class.getName());
  private static final int BUFFER_SIZE = 1_024;
  private final Server server;
  private boolean closed = false;
  private String login;

  public ServerContext(Server server, SelectionKey key) {
    super(key, BUFFER_SIZE);
    this.server = server;
  }

  @Override
  public void processCurrentOpcodeAction(Frame frame) throws IOException {
    switch (frame) {
      case Register register -> {
        if (isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is already authenticated");
          silentlyClose();
          return;
        }

        login = register.username();

        if (!server.addClient(login, super.getSocket())) {
          logger.warning(STR."Login \{login} already in use");
          silentlyClose();
          return;
        }

        logger.info(STR."Client \{super.getSocket().getRemoteAddress()} has logged in as \{login}");

        // Send an OK message to the client
        queueFrame(new OK());
        server.broadcast(new Event((byte) 1, login));
      }

      case Discovery _ -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.discovery(this);
      }

      case YellMessage yellMessage -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }

        var newMessage = new YellMessage(yellMessage.login(), yellMessage.txt(), System.currentTimeMillis());
        server.broadcast(newMessage);
      }

      case WhisperMessage whisperMessage -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.whisper(whisperMessage, login);
      }

      case Propose propose -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.propose(propose.codex(), login);
      }

      case Request request -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        server.request(request.codexId(), this);
      }

      case RequestDownload requestDownload -> {
        if (!isAuthenticated()) {
          logger.warning(STR."Client \{super.getSocket().getRemoteAddress()} is not authenticated");
          silentlyClose();
          return;
        }
        if (requestDownload.mode() == 0) {
          server.requestOpenDownload(requestDownload.codexId(), this);
        } else {
          // TODO : implement closed download
        }
      }

      default -> {
        logger.warning("No action for the received frame ");
        silentlyClose();
      }
    }
  }

  public String login() {
    return login;
  }

  private boolean isAuthenticated() {
    return login != null;
  }

  @Override
  public void silentlyClose() {
    server.removeClient(login);
    super.silentlyClose();
  }
}