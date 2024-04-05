package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.client.ClientAPI;
import fr.uge.chadow.client.ClientController;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class Display {
  private static final Logger logger = Logger.getLogger(Display.class.getName());
  private final ClientController controller;
  private final ClientAPI api;
  private final ReentrantLock lock = new ReentrantLock();
  private int lines;
  private int cols;
  
  public Display(int lines, int cols, ClientController  controller, ClientAPI api) {
    Objects.requireNonNull(controller);
    Objects.requireNonNull(api);
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and cols must be positive");
    }
    this.api = api;
    this.controller = controller;
    this.lines = lines;
    this.cols = cols;
  }
  
  static String lowInfoBar(int columns) {
    return STR."\{String.valueOf(CLIColor.CYAN)}\{CLIColor.BOLD}\{"\u25A0".repeat(columns)}\{CLIColor.RESET}\n";
  }
  
  /**
   * Set the dimensions of the view
   *
   * @param lines
   * @param cols
   */
  public void setDimensions(int lines, int cols) {
    if (lines <= 0 || cols <= 0) {
      throw new IllegalArgumentException("lines and columns must be positive");
    }
    lock.lock();
    try {
      this.lines = lines;
      this.cols = cols;
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Start the loop that will refresh the view
   */
  public void startLoop() throws InterruptedException, IOException {
    System.out.print(CLIColor.CLEAR);
    System.out.flush();
    while (!Thread.interrupted()) {
      if (controller.viewCanRefresh().get()) {
        draw();
      }
      Thread.sleep(200);
    }
  }
  
  public void clear() {
    View.moveCursorToPosition(1, 1);
    View.clearDisplayArea(lines);
    View.moveCursorToPosition(1, 1); // Move cursor back to the top
  }
  
  /**
   * Draw the view in the current mode
   *
   * @throws IOException
   */
  public void draw() throws IOException {
    controller.currentView().draw();
    //System.out.print(View.thematicBreak(cols));
    System.out.print(lowInfoBar(cols));
    System.out.print(inputField());
    View.moveToInputField(lines);
  }
  
  private String inputField() {
    var inputField = "";
    if (!controller.viewCanRefresh().get()) {
      // (getMaxUserLength() + 21)
      inputField = ("%s\n")
          .formatted(STR."[\{CLIColor.BOLD}\{CLIColor.CYAN}\{api.login()}\{CLIColor.RESET}]");
    } else {
      // (getMaxUserLength() + 50)
      inputField = ("%s\n")
          .formatted(STR."\{CLIColor.GREY}[\{CLIColor.GREY}\{CLIColor.BOLD}\{api.login()}\{CLIColor.RESET}\{CLIColor.GREY}]\{CLIColor.RESET}");
    }
    return inputField + View.inviteCharacter() + CLIColor.BOLD;
  }
  
}