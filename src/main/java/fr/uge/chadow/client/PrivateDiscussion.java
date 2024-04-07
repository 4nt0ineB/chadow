package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.WhisperMessage;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;


/**
 * Represents a recipient for private messages.
 * Allows to store messages and retrieve them.
 * If the recipient is not connected,
 * the messages will be stored under a new recipient username.
 */
public class PrivateDiscussion {
  private static final Logger logger = Logger.getLogger(PrivateDiscussion.class.getName());
  private final UUID id;
  private String username;
  private final List<WhisperMessage> discussion = new ArrayList<>();
  private final ReentrantLock lock = new ReentrantLock();
  private volatile boolean hasNewMessages = false;
  
  PrivateDiscussion(UUID id, String username) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(username);
    this.id = id;
    this.username = username;
    logger.info("New private discussion with " + username);
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
      discussion.add(message);
      logger.info("New message from " + message.username()  + " and current is " + username + " => means" + username.equals(message.username()));
      if(message.username().equals(username)) {
        hasNewMessages = true;
        logger.info("New message from " + username);
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
      return List.copyOf(discussion);
    } finally {
      lock.unlock();
    }
  }

  
  public boolean hasMessages() {
    lock.lock();
    try {
      return !discussion.isEmpty();
    } finally {
      lock.unlock();
    }
  }
  
  public void clearMessages() {
    lock.lock();
    try {
      discussion.clear();
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
      discussion.stream().map(message -> {
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
  
  
  public boolean hasNewMessages() {
    lock.lock();
    try {
      return hasNewMessages;
    } finally {
      lock.unlock();
    }
  }
}