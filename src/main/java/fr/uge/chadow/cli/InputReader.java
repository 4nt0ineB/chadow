package fr.uge.chadow.cli;

import fr.uge.chadow.cli.display.Display;
import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class InputReader {
  
  private static final Logger logger = Logger.getLogger(InputReader.class.getName());
  private final Client client;
  private final AtomicBoolean viewCanRefresh;
  private int lines;
  private int columns;
  private final Display display;
  
  public InputReader(Client client, int lines, int columns, AtomicBoolean viewCanRefresh, Display display) {
    Objects.requireNonNull(client);
    Objects.requireNonNull(display);
    if(lines <= 0 || columns <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.viewCanRefresh = viewCanRefresh;
    this.display = display;
    this.lines = lines;
    this.columns = columns;
  }
  
  private boolean processInput(String input) throws InterruptedException {
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
          display.setDimensions(x, y);
        } catch (NumberFormatException e) {
          return false;
        }
        return false;
      }
    }
    return switch (input) {
      // global commands and commands to change view
      case ":exit" -> {
        throw new Error("TODO exit");
        //yield false;
      }
      // specific mode commands
      default -> display.processInput(input);
    };
  }
  
  /**
   * Read user input from the console
   * We talk about writeMode when autoRefresh is disabled.
   * In autoRefresh mode (set to true), incoming messages are displayed automatically.
   * The user can press enter to stop the auto refresh
   * to be able to type a message (or a command).
   * When the user press enter again the message (or the command) is processed,
   * then the display goes back into autoRefresh mode.
   *
   * In writeMode (autoRefresh set to false), the user can escape a line with '\' followed by enter.
   * This is useful to write a multiline message.
   *
   *
   * @throws IOException
   */
  public void start() throws IOException, InterruptedException {
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    var inputField = "";
    var c = 0;
    var escape = false;
    var numberOfLineBreak = 0;
    
    while ((c = reader.read()) != -1) {
      if(viewCanRefresh.get()) {
        if (c == '\n') {
          viewCanRefresh.set(false);
          View.clearDisplayAndMore(lines, numberOfLineBreak);
          display.draw();
        }
      }else {
        if (escape && !inputField.endsWith("\n")) {
          // escape '\' is replaced as line break in the inputField
          System.out.print("> ");
          inputField += '\n';
          escape = false;
        } else {
          // we escape with '\'
          if (c == '\\' ) {
            escape = true;
            numberOfLineBreak++;
          } else if (c == '\n') {
            View.clearDisplayAndMore(lines, numberOfLineBreak);
            var letWrite = processInput(inputField);
            display.draw();
            viewCanRefresh.set(!letWrite);
            inputField = "";
            numberOfLineBreak = 0;
          } else {
            inputField += (char) c;
          }
        }
      }
    }
  }
  
}

// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238 2>logs
// tail -f logs