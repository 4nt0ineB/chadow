package fr.uge.chadow.cli.display;


import java.util.Objects;

public class Scroller{
    private int currentLine;
    private int lines;
    private final int page;
    
    public Scroller(int lines, int page) {
        this.page = page;
        setLines(lines);
    }
    
    public void setCurrentLine(int currentLine) {
        Objects.checkIndex(currentLine, lines);
        this.currentLine = currentLine;
    }
    
    /**
     * Reset the scroller
     * @param lines
     */
    public void setLines(int lines) {
        this.lines = lines;
        currentLine = Math.max(lines - page, 0);
    }
    
    public void scrollDown(int n) {
        if (currentLine < lines) {
            currentLine = Math.min(lines, currentLine + n);
        }
    }

    public void scrollUp(int n) {
        if (currentLine > 0) {
            currentLine = Math.max(0, currentLine - n);
        }
    }
    
    public int getA() {
        return currentLine;
    }
    
    public int getB() {
        return Math.min(lines, currentLine + page);
    }
}
