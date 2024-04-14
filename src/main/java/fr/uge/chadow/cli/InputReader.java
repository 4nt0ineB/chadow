package fr.uge.chadow.cli;

import fr.uge.chadow.client.ClientConsoleController;

import java.util.Objects;

public class InputReader {
  
  private final ClientConsoleController controller;
  
  public InputReader(ClientConsoleController controller) {
    Objects.requireNonNull(controller);
    this.controller = controller;
  }
  
  
  
}

// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238 2>logs
// tail -f logs