package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.WhisperMessage;

import java.util.*;

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;


/**
 * Represents a recipient for private messages.
 * Allows to store messages and retrieve them.
 * If the recipient is not connected,
 * the messages will be stored under a new recipient username.
 */
public class DirectMessages {
  private static final Logger logger = Logger.getLogger(DirectMessages.class.getName());
  private final UUID id;
  private String username;
  private final List<WhisperMessage> messages = new ArrayList<>();
  private final ReentrantLock lock = new ReentrantLock();
  private volatile boolean hasNewMessages = false;
  
  DirectMessages(UUID id, String username) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(username);
    this.id = id;
    this.username = username;
    logger.info("New dm with " + username);
  }
  
  public UUID id() {
    lock.lock();
    try {
      return id;
    } finally {
      lock.unlock();
    }
  }
  
  public String username() {
    lock.lock();
    try {
      return username;
    } finally {
      lock.unlock();
    }
  }
  
  public void addMessage(WhisperMessage message) {
    lock.lock();
    try {
      messages.add(message);
      if(message.username().equals(username)) {
        hasNewMessages = true;
      }
      
    } finally {
      lock.unlock();
    }
  }
  
  public void markAsRead() {
    lock.lock();
    try {
      hasNewMessages = false;
    } finally {
      lock.unlock();
    }
  }
  
  public List<WhisperMessage> messages() {
    lock.lock();
    try {
      markAsRead();
      return List.copyOf(messages);
    } finally {
      lock.unlock();
    }
  }

  
  public boolean hasMessages() {
    lock.lock();
    try {
      return !messages.isEmpty();
    } finally {
      lock.unlock();
    }
  }
  
  public Optional<WhisperMessage> getLastMessage() {
    lock.lock();
    try {
      if(messages.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(messages.getLast());
    } finally {
      lock.unlock();
    }
  }
  
  public void clearMessages() {
    lock.lock();
    try {
      messages.clear();
    } finally {
      lock.unlock();
    }
  }
  
  /**
   * Change the username of the recipient.
   * This will also change the username of all the messages
   * sent by the recipient.
   * @param newUsername the new username of the recipient
   */
  void changeUsername(String newUsername) {
    lock.lock();
    try {
      messages.stream().map(message -> {
        if(message.username().equals(username)) {
          return  new WhisperMessage(newUsername, message.txt(), message.epoch());
        }
        return message;
      });
      username = newUsername;
    } finally {
      lock.unlock();
    }
  }
  
  public static DirectMessages create(String username) {
    var newDM = new DirectMessages(UUID.randomUUID(), username);
    var startMessage = new WhisperMessage("", STR."This is the beginning of your direct message history with \{username}", System.currentTimeMillis());
    newDM.addMessage(startMessage);
    return newDM;
  }
  
  public boolean hasNewMessages() {
    lock.lock();
    try {
      return hasNewMessages;
    } finally {
      lock.unlock();
    }
  }
  
}