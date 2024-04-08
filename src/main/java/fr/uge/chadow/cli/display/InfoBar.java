package fr.uge.chadow.cli.display;

import fr.uge.chadow.cli.CLIColor;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Consumer;

public class InfoBar {
  
  private final PriorityQueue<Message> messages;
  private final Consumer<InfoBar> updater;
  private int cols;
  
  
  public InfoBar(int cols, Consumer<InfoBar> updater) {
    this.cols = cols;
    this.updater = updater;
    messages = new PriorityQueue<>(Comparator.comparingLong(Message::timestamp)
                                             .reversed()
                                             .thenComparing(m -> switch (m.priority()) {
                                               case ERROR -> 0;
                                               case INFO -> 1;
                                             }));
  }
  
  public void draw() {
    updater.accept(this);
    var sb = new StringBuilder();
    sb.append((STR."\{CLIColor.RESET}[%s] ").formatted(View.formatDate(System.currentTimeMillis())));
    for (var message : messages) {
      sb.append(message.content);
    }
    var lengthWithoutEscapeCodes = CLIColor.countLengthWithoutEscapeCodes(sb.toString());
    var numberOfEscapeCodes = sb.length() - lengthWithoutEscapeCodes;
    var length = Math.min(sb.length(), cols + numberOfEscapeCodes);
    System.out.print(sb.substring(0, length) + CLIColor.RESET);
    System.out.printf((STR."\{STR."\{CLIColor.CYAN}%s\{CLIColor.RESET}"}%n"), "\u25A0".repeat(cols - length + numberOfEscapeCodes));
  }
  
  public void setDimensions(int cols) {
    this.cols = cols;
  }
  
  public void clear() {
    messages.clear();
  }
  
  public void addError(String error) {
    addMessage(Message.PRIORITY.ERROR, error);
  }
  
  public void addInfo(String info) {
    addMessage(Message.PRIORITY.ERROR, info);
  }
  
  private void addMessage(Message.PRIORITY priority, String content) {
    messages.add(new Message(priority, content, System.currentTimeMillis()));
  }
  
  public void clearErrors() {
  
  }
  
  private record Message(PRIORITY priority, String content, long timestamp) {
    enum PRIORITY {
      ERROR,
      INFO,
    }
    
  }
  
  
}