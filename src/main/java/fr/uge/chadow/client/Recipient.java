package fr.uge.chadow.client;

import fr.uge.chadow.core.protocol.WhisperMessage;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class Recipient {
  private final UUID id;
  private final String username;
  private final List<WhisperMessage> messages = new ArrayList<>();
  private final ReentrantLock lock = new ReentrantLock();
  
  Recipient(UUID id, String username) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(username);
    this.id = id;
    this.username = username;
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
    } finally {
      lock.unlock();
    }
  }
  
  public List<WhisperMessage> messages() {
    lock.lock();
    try {
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
  
}