package fr.uge.chadow;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SettingsParser {
  private static final Logger logger = Logger.getLogger(SettingsParser.class.getName());
  private final Map<String, Settings.Setting<?>> settingsReaders = new HashMap<>();
  
  public SettingsParser addAsInt(String name, Integer defaultValue) {
    addSettingImpl(name, int.class, defaultValue);
    return this;
  }
  
  public SettingsParser addAsString(String name, String defaultValue) {
    addSettingImpl(name, String.class, defaultValue);
    return this;
  }
  
  public SettingsParser addAsBoolean(String name, Boolean defaultValue) {
    addSettingImpl(name, boolean.class, defaultValue);
    return this;
  }
  
  private <T> void  addSettingImpl(String name, Class<T> expected, T defaultValue) {
    var old = settingsReaders.put(name, new Settings.Setting<>(name, expected, defaultValue));
    if(old != null) {
      throw new IllegalArgumentException(STR."Setting \"\{name}\" already exists");
    }
  }
  
  
  public Settings parse(String formattedSettings) throws IOException {
    Objects.requireNonNull(formattedSettings);
    var parsedSettings = new Settings();
    var allSettings = new HashSet<>(settingsReaders.keySet());
    var it = Arrays.stream(formattedSettings.split("--")).skip(1).iterator();
    while(it.hasNext()) {
      var setting = it.next().trim();
      var parts = setting.split(":");
      logger.info(STR."Setting: \{parts[0]} - \{parts[1]}");
      if (parts.length != 2) {
        throw new IllegalArgumentException(STR."Invalid setting \"\{setting}\"");
      }
      var name = parts[0];
      var value = parts[1];
      var sr = settingsReaders.get(name);
      if (sr == null) {
        throw new IOException(STR."Unknown setting \"\{name}\"");
      }
      allSettings.remove(name);
      parsedSettings.addSetting(sr.expectedType(), name, value);
    }
    // defaults
    for(var sr: settingsReaders.values()) {
      if(sr.defaultValue() != null && allSettings.contains(sr.name())) {
        allSettings.remove(sr.name());
        if(sr.expectedType == String.class) {
          parsedSettings.addStringSettings(sr.name(), (String) sr.defaultValue());
        } else if(sr.expectedType == int.class) {
          parsedSettings.addIntSettings(sr.name(), (Integer) sr.defaultValue());
        } else if(sr.expectedType == boolean.class) {
          parsedSettings.addBooleanSettings(sr.name(), (Boolean) sr.defaultValue());
        } else {
          throw new AssertionError("Unexpected setting type");
        }
      }
    }
    
    if(!allSettings.isEmpty()) {
      throw new IOException(STR."Missing settings: \{allSettings}");
    }
    return parsedSettings;
  }
  
  
  
  
}