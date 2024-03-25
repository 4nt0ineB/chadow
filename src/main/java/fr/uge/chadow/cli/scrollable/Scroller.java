package fr.uge.chadow.cli.scrollable;

import fr.uge.chadow.cli.scrollable.Scrollable;

public class Scroller {
    private final Scrollable scrollable;
    private final int lines;
    private int currentLine = 0;

    public Scroller(Scrollable scrollable, int lines) {
        this.scrollable = scrollable;
        this.lines = lines;
    }

    public void scrollUp() {
        if (currentLine > 0) {
            scrollable.scrollUp();
            currentLine--;
        }
    }

    public void scrollDown() {
        if (currentLine < lines) {
            scrollable.scrollDown();
            currentLine++;
        }
    }
}
