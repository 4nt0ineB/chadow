package fr.uge.chadow.cli;

import fr.uge.chadow.cli.display.View;
import fr.uge.chadow.client.ClientController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.logging.Logger;

public class InputReader {
  
  private final ClientController controller;
  
  public InputReader(ClientController controller) {
    Objects.requireNonNull(controller);
    this.controller = controller;
  }
  
  /**
   * Read user input from the console
   * We talk about writeMode when autoRefresh is disabled.
   * In autoRefresh mode (set to true), incoming messages are displayed automatically.
   * The user can press enter to stop the auto refresh
   * to be able to type a message (or a command).
   * When the user press enter again the message (or the command) is processed,
   * then the display goes back into autoRefresh mode.
   * <p>
   * In writeMode (autoRefresh set to false), the user can escape a line with '\' followed by enter.
   * This is useful to write a multiline message.
   *
   * @throws IOException
   */
  public void start() throws IOException, InterruptedException, NoSuchAlgorithmException {
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    StringBuilder inputField = new StringBuilder();
    var c = 0;
    var escape = false;
    while ((c = reader.read()) != -1) {
      var canRefresh = controller.viewCanRefresh().get();
      if (canRefresh) {
        if (c == '\n') {
          controller.stopAutoRefresh();
        }
      } else {
        if (escape && !inputField.toString().endsWith("\n")) {
          // escape '\' is replaced as line break in the inputField
          System.out.print(View.inviteCharacter());
          inputField.append('\n');
          escape = false;
        } else {
          // we escape with '\'
          if (c == '\\') {
            escape = true;
          } else if (c == '\n') {
            controller.processInput(inputField.toString());
            inputField.setLength(0);
          } else {
            inputField.append((char) c);
          }
        }
      }
    }
  }
  
}

// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238 2>logs
// tail -f logs