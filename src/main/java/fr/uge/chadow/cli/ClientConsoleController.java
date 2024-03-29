package fr.uge.chadow.cli;

import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.cli.display.Scroller;
import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.client.Client;
import fr.uge.chadow.core.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class ClientConsoleController {
  private final static Logger logger = Logger.getLogger(ClientConsoleController.class.getName());
  
  public enum Mode {
    CHAT_LIVE_REFRESH,
    CHAT_SCROLLER,
    USERS_SCROLLER,
    HELP_SCROLLER,
  }
  
  private final Client client;
  private final InputReader inputReader;
  private final Display display;
  private int lines;
  private int cols;
  
  public ClientConsoleController(Client client, int lines, int cols) {
    Objects.requireNonNull(client);
    if(lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.lines = lines;
    this.cols = cols;
    AtomicBoolean viewCanRefresh = new AtomicBoolean(true);
    display = new Display(lines, cols, client.login(), client.serverHostName(), viewCanRefresh, this);
    inputReader = new InputReader(viewCanRefresh, this);
  }
  
  public void start()  {
    //System.err.println("----------------------Running with " + lines + " lines and " + cols + " columns");
    
    // client
    Thread.ofPlatform().daemon().start(() -> {
      try {
        client.launch();
      } catch (IOException e) {
        // TODO: handle exception -> inform the user and quit
        throw new RuntimeException(e);
      }
    });
    display.draw();
    // thread that manages the display
    Thread.ofPlatform().daemon().start(display::startLoop);
    // for dev: fake messages
    fillWithFakeData();
    client.subscribe(display::addMessage);
    // Thread that manages the user inputs
    try {
      inputReader.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
     logger.severe("User input reader was interrupted." + e.getMessage());
    }
  }
  
  /**
   * Process the message or the command
   *
   * @param input
   * @return true if the user can type again, otherwise it's the view's turn.
   * @throws InterruptedException
   */
  public boolean processInput(String input) throws InterruptedException {
    if(input.startsWith(":r ")) {
      var split = input.split(" ");
      if(split.length == 3) {
        try {
          var x = Integer.parseInt(split[1]);
          var y = Integer.parseInt(split[2]);
          if(x <= 0 || y <= 0) {
            return false;
          }
          View.clearDisplayArea(lines);
          lines = y;
          cols = x;
          display.setDimensions(x, y);
        } catch (NumberFormatException e) {
          return false;
        }
        return false;
      }
    }
    return switch (input) {
      case ":m", ":message" -> {
        display.setMode(Mode.CHAT_SCROLLER);
        yield true;
      }
      case ":u", ":users" -> {
        display.setMode(Mode.USERS_SCROLLER);
        yield true;
      }
      case ":c", ":chat" -> {
        display.setMode(Mode.CHAT_LIVE_REFRESH);
        yield false;
      }
      case ":exit" -> {
        exitNicely();
        yield false;
      }
      case ":h", ":help" -> {
        display.setMode(Mode.HELP_SCROLLER);
        yield true;
      }
      // specific mode commands
      default -> switch (display.getMode()) {
        case CHAT_LIVE_REFRESH -> processInputModeLiveRefresh(input);
        case CHAT_SCROLLER, USERS_SCROLLER, HELP_SCROLLER -> processInputModeScroller(input);
      };
    };
  }
  
  public void drawDisplay() {
    display.clear();
    display.draw();
  }

  private boolean processInputModeLiveRefresh(String input) throws InterruptedException {
    if (!input.startsWith(":") && !input.isBlank()) {
      client.sendMessage(input);
    }
    return false;
  }
  
  private boolean processInputModeScroller(String input) {
    switch (input) {
      case "e" -> display.scrollerUp();
      case "s" -> display.scrollerDown();
    }
    return true;
  }
  
  public void clearDisplayAndMore(int numberOfLineBreak) {
    View.clearDisplayAndMore(lines, numberOfLineBreak);
  }
  
  private void fillWithFakeData() {
    var users = new String[]{"test", "Morpheus", "Trinity", "Neo", "Flynn", "Alan", "Lora", "Gandalf", "ThorinSonOfThrainSonOfThror", "Bilbo", "SKIDROW", "Antoine"};
    for(var user: users){
      display.addUser(user);
    }
    var messages = new Message[] {
        new Message("test", "test", System.currentTimeMillis()),
        new Message("test", "hello how are you", System.currentTimeMillis()),
        new Message("Morpheus", "Wake up, Neo...", System.currentTimeMillis()),
        new Message("Morpheus", "The Matrix has you...", System.currentTimeMillis()),
        new Message("Morpheus", "Follow the white rabbit", System.currentTimeMillis()),
        new Message("Neo", "what the hell is this", System.currentTimeMillis()),
        new Message("Neo", "Just going to bed now", System.currentTimeMillis()),
        new Message("Alan1", "Master CONTROL PROGRAM\nRELEASE TRON JA 307020...\nI HAVE PRIORITY ACCESS 7", System.currentTimeMillis()),
        new Message("SKIDROW", "Here is the codex of the FOSS (.deb) : cdx:1eb49a28a0c02b47eed4d0b968bb9aec116a5a47", System.currentTimeMillis()),
        new Message("Antoine", "Le lien vers le sujet : http://igm.univ-mlv.fr/coursprogreseau/tds/projet2024.html", System.currentTimeMillis())
    };
    for (var message: messages) {
      display.addMessage(message);
    }
    // start a thread that adds a message every second
    /*Thread.ofPlatform().daemon().start(() -> {
      while (true) {
        try {
          Thread.sleep(1000); // Sleep for 1 second
          display.addMessage(new Message("test", "test", System.currentTimeMillis()));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });*/
  }
  
 /* public void launchMessageFetcher() {
    Thread.ofPlatform().daemon().start(() -> {
      while (!Thread.interrupted()) {
        try {
          var messages = client.getLastMessages(1);
          for (var message : messages) {
            display.addMessage(message);
          }
          Thread.sleep(300);
        } catch (InterruptedException e) {
        
        }
      }
    });
  }*/
  
  private void exitNicely() {
    // inputReader.stop();
    // display.stop();
    // client.stop();
    
    // just for now
    System.exit(0);
  }
}
