package fr.uge.chadow.client.cli.display.view;

import fr.uge.chadow.client.CodexStatus;
import fr.uge.chadow.client.cli.CLIColor;
import fr.uge.chadow.client.cli.display.View;
import static fr.uge.chadow.client.cli.display.View.colorize;
import fr.uge.chadow.core.protocol.field.Codex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CodexView implements View {
  private static final Logger logger = Logger.getLogger(CodexView.class.getName());
  private final CodexStatus codexStatus;
  private final int lines;
  private final int cols;
  
  private ScrollableView scrollableView;
  
  public CodexView(CodexStatus codexStatus, int lines, int cols) {
    this.codexStatus = codexStatus;
    this.lines = lines;
    this.cols = cols;
    scrollableView = toView(codexStatus, lines, cols);
  }
  
  @Override
  public void setDimensions(int lines, int cols) {
    scrollableView.setDimensions(lines, cols);
  }
  
  @Override
  public void draw() throws IOException {
    var newview = toView(codexStatus, lines, cols);
    scrollableView.setAsSamePosition(newview);
    scrollableView = newview;
    scrollableView.draw();
    logger.info("Drawing CodexView");
  }
  
  private static ScrollableView toView(CodexStatus codexStatus, int lines, int cols) {
    var codex = codexStatus.codex();
    var sb = new StringBuilder();
    var splash = """
            ## ┏┓   ┓
            ## ┃ ┏┓┏┫┏┓┓┏
            ## ┗┛┗┛┻┗┗━┛┗
            
            """;
    sb.append(splash);
    sb.append("cdx:")
      .append(codex.id())
      .append("\n");
    if (codexStatus.isComplete()) {
      sb.append(CLIColor.CYAN)
        .append("▓ Complete\n")
        .append(CLIColor.RESET);
    }
    if (codexStatus.isDownloading() || codexStatus.isSharing()) {
      sb.append(CLIColor.ITALIC)
        .append(CLIColor.BOLD)
        .append(CLIColor.ORANGE)
        .append(codexStatus.isDownloading() ? (STR."▓ Downloading ...\{codexStatus.isDownloadingHidden() ? "(hidden)" : ""}"):
            (codexStatus.isSharing() ? "▓ Sharing... " : ""))
        .append(CLIColor.RESET)
        .append("\n");
      
      if(codexStatus.isDownloading() && !codexStatus.isComplete()) { // progressbar on the full screen length
        var max = cols;
        var progress = (int) (max * codexStatus.completionRate());
        sb.append("[").append("■".repeat(progress)).append(" ".repeat((max - (progress)) - 3)).append("]\n");
      }
    }
    sb.append("\n");
    sb.append(View.colorize(CLIColor.BOLD, "Title: "))
      .append(codex.name())
      .append("\n");
    var infoFiles = codex.files();
    sb.append(colorize(CLIColor.BOLD, "Number of files:  "))
      .append(infoFiles.length)
      .append("\n");
    sb.append(colorize(CLIColor.BOLD, "Total size:   "))
      .append(View.bytesToHumanReadable(codex.totalSize()))
      .append("\n");
    sb.append("Local Path: ")
      .append(Path.of(codexStatus.root(), codexStatus.codex().name()))
      .append("\n\n");
    sb.append(colorize(CLIColor.BOLD, "Files:  \n"));
    
    Arrays.stream(infoFiles)
          .collect(Collectors.groupingBy(Codex.FileInfo::relativePath))
          .forEach((dir, files) -> {
            sb.append(colorize(CLIColor.BOLD, STR."[\{dir}]\n"));
            files.forEach(file -> sb.append("\t- ")
                                    .append(CLIColor.BOLD)
                                    .append("%10s".formatted(View.bytesToHumanReadable(file.length())))
                                    .append("  ")
                                    .append("%.2f%%".formatted(codexStatus.completionRate(file) * 100))
                                    .append("  ")
                                    .append(CLIColor.RESET)
                                    .append(file.filename())
                                    .append("\n"));
          });
    
   var aa = View.scrollableFromString(STR."[Codex] \{codex.name()}", lines, cols, sb.toString());
   
   return aa;
  }
  
  @Override
  public void clear() {
    scrollableView.clear();
    View.clear(lines);
  }
  
  @Override
  public void scrollPageUp() {
    scrollableView.scrollPageUp();
  }
  
  @Override
  public void scrollPageDown() {
    scrollableView.scrollPageDown();
  }
  
  @Override
  public void scrollBottom() {
     scrollableView.scrollBottom();
  }
  
  @Override
  public void scrollTop() {
    scrollableView.scrollTop();
  }
  
  @Override
  public void scrollLineDown() {
    scrollableView.scrollLineDown();
  }
  
  @Override
  public void scrollLineUp() {
    scrollableView.scrollLineUp();
  }
}