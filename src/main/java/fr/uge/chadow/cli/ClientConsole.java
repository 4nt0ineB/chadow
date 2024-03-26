package fr.uge.chadow.cli;

import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.client.Client;
import fr.uge.chadow.core.Message;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientConsole {
  private final InputReader inputReader;
  private final Display display;
  private final AtomicBoolean viewCanRefresh = new AtomicBoolean(true);
  
  public ClientConsole(Client client, int lines, int cols) {
    Objects.requireNonNull(client);
    if(lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    display = new Display(client, lines, cols, viewCanRefresh);
    inputReader = new InputReader(client, lines, cols, viewCanRefresh, display);
  }
  
  public void start() throws InterruptedException, IOException {
    //System.err.println("----------------------Running with " + lines + " lines and " + cols + " columns");
    display.draw();
    // thread that manages the display
    Thread.ofPlatform().daemon().start(display::startLoop);
    
    // for dev: fake messages
    fillWithFakeData();
    
    // Thread that manages the user inputs
    inputReader.start();
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
    Thread.ofPlatform().daemon().start(() -> {
      while (true) {
        try {
          Thread.sleep(1000); // Sleep for 1 second
          display.addMessage(new Message("test", "test", System.currentTimeMillis()));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
  }

}
