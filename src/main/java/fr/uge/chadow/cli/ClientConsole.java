package fr.uge.chadow.cli;

import fr.uge.chadow.cli.view.Chat;
import fr.uge.chadow.cli.view.View;
import fr.uge.chadow.client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class ClientConsole {
  
  private static final Logger logger = Logger.getLogger(ClientConsole.class.getName());
  private final Client client;
  private final AtomicBoolean viewCanDisplay = new AtomicBoolean(true);
  private int lines;
  private int columns;
  private final Chat chat;
  
  
  private final ArrayBlockingQueue<View> viewQueue = new ArrayBlockingQueue<>(1);
  
  public ClientConsole(Client client, int lines, int columns) {
    Objects.requireNonNull(client);
    if(lines <= 0 || columns <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    this.client = client;
    this.chat = new Chat(client, lines, columns, viewCanDisplay);
    this.lines = lines;
    this.columns = columns;
  }
  

  
  
  public void start() throws IOException, InterruptedException {
    
    System.out.println("Running with " + lines + " lines and " + columns + " columns");
    viewQueue.put(chat);
    
    // Thread that display
    Thread.ofPlatform().daemon().start(() -> {
      var view = viewQueue.poll();
      viewQueue.peek();
      view.pin();
    });
    
    // Thread that process the user inputs
    userInput();
 
  }
  
  /**
   * Read user input from the console
   * We talk about writeMode when autoRefresh is disabled.
   * In autoRefresh mode (set to true), incoming messages are displayed automatically.
   * The user can press enter to stop the auto refresh
   * to be able to type a message (or a command).
   * When the user press enter again the message (or the command) is processed,
   * then the chat goes back into autoRefresh mode.
   *
   * In writeMode (autoRefresh set to false), the user can escape a line with '\' followed by enter.
   * This is useful to write a multiline message.
   *
   *
   * @throws IOException
   */
  private void userInput() throws IOException, InterruptedException {
    // input
    var maxUserLength = client.login().length();
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String inputField = "";
    var c = 0;
    var escape = false;
    
    while ((c = reader.read()) != -1) {
      if(viewCanDisplay.get()) {
        if (c == '\n') {
          var refresh = viewCanDisplay.get();
          viewCanDisplay.set(!refresh);
          chat.drawDiscussionThread();
        }
      }else {
        if (escape && !inputField.endsWith("\n")) {
          System.out.print((" ".repeat(maxUserLength + 8) + "> "));
          inputField += '\n';
          escape = false;
        } else {
          // we escape with '\', and is stored has line break
          if (c == '\\' ) {
            escape = true;
          } else if (c == '\n') {
            chat.processInput(inputField);
            inputField = "";
            var refresh = viewCanDisplay.get();
            viewCanDisplay.set(!refresh);
          } else {
            inputField += (char) c;
          }
        }
      }
    }
  }
  
  
}


// stty size
//  java -jar --enable-preview target/chadow-1.0.0.jar localhost 7777 25 238