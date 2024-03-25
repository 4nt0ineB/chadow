package fr.uge.chadow.cli.view;

import fr.uge.chadow.cli.CLIColor;
import fr.uge.chadow.core.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Chat implements View{
    
    private final List<Message> messages = new ArrayList<>();
    private final List<String> users = new ArrayList<>();
    private final LinkedBlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();
    private int lines;
    private int columns;
    private AtomicBoolean autoRefresh = new AtomicBoolean(true);
    
  
    @Override
    public void processInput(String input) {
        System.out.println(input);
    }
    
    @Override
    public void setDimensions(int lines, int cols) {
    
    }
    
    
    /**
     * Main chat loop
     * @throws IOException
     */
    public void chat_loop() throws IOException {
        System.out.print(CLIColor.CLEAR);
        System.out.flush();
        var maxUserLength = getMaxUserLength();
        var position = new int[]{maxUserLength + 10, lines};
        while (true) {
            try {
                if(autoRefresh.get()) {
                    drawDiscussionThread();
                    // Wait for incoming messages
                    var incomingMessage = messagesQueue.take();
                    messages.add(incomingMessage);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Draw the chat area
     * @throws IOException
     */
    private void drawDiscussionThread() throws IOException {
        var maxUserLength = getMaxUserLength();
        var position = new int[]{maxUserLength + 10, lines};
        View.moveCursorToPosition(1, 1);
        clearChatArea();
        printChatDisplay();
        View.moveCursorToPosition(position[0], position[1]);
    }
    
    
    
    /**
     * Get the max length of the usernames
     * Default size is 5
     * @return
     */
    private int getMaxUserLength() {
        return Math.max(users.stream().mapToInt(String::length).max().orElse(0), 5);
    }
}
