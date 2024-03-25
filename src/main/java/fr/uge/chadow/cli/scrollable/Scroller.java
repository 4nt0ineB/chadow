package fr.uge.chadow.cli.scrollable;


public class Scroller{
    private int currentLine = 0;
    private int lines = 0;
    
    public Scroller(int lines) {
        this.lines = lines;
    }
    
    /**
     * Reset the scroller
     * @param lines
     */
    public void setLines(int lines) {
        this.lines = lines;
        currentLine = 0;
    }
    
    public void scrollUp() {
        if (currentLine > 0) {
            currentLine--;
        }
    }

    public void scrollDown() {
        if (currentLine < lines) {
            currentLine++;
        }
    }
    
    public int at(int index) {
        return currentLine + index;
    }
}
