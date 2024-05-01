package fr.uge.chadow;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


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
  
  /**
   * Add a setting that is expected to be a string.
   * These a required
   *
   * @param names
   */
  public SettingsParser addStringSettings(String... names) {
    Objects.requireNonNull(names);
    addSettingImpl(String.class, names);
    return this;
  }
  
  /**
   * Add a setting that is expected to be an int.
   * These are required
   *
   * @param names
   */
  public SettingsParser addIntSettings(String... names) {
    Objects.requireNonNull(names);
    addSettingImpl(int.class, names);
    return this;
  }
  
  private void addSettingImpl(Class<?> expectedType, String... names) {
    for (var name : names) {
      addSettingImpl(name, expectedType, null);
    }
  }
  
  private <T> void  addSettingImpl(String name, Class<T> expected, T defaultValue) {
    var old = settingsReaders.put(name, new Settings.Setting<T>(name, expected, defaultValue));
    if(old != null) {
      throw new IllegalArgumentException(STR."Setting \"\{name}\" already exists");
    }
  }
  
  
  public Settings parse(String formattedSettings) throws IOException {
    Objects.requireNonNull(formattedSettings);
    var parsedSettings = new Settings();
    
    var allSettings = settingsReaders.keySet().stream().collect(Collectors.toCollection(HashSet::new));
    
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
      if (sr.expectedType == String.class) {
        parsedSettings.stringSettings.put(name, value);
      } else if (sr.expectedType == int.class) {
        int intValue;
        try {
          intValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new IOException(STR."Invalid integer value \"\{value}\" for setting \"\{name}\"");
        }
        parsedSettings.intSettings.put(name, intValue);
      } else if (sr.expectedType == boolean.class) {
        if (!Pattern.compile("true|false|1|0").matcher(value).matches()) {
          throw new IOException(STR."Invalid boolean value \"\{value}\" for setting \"\{name}\"");
        }
        parsedSettings.stringSettings.put(name, value);
      } else {
        throw new AssertionError("Unexpected setting type");
      }
    }
    // defaults
    for(var sr: settingsReaders.values()) {
      if(sr.defaultValue != null && allSettings.contains(sr.name)) {
        allSettings.remove(sr.name);
        if(sr.expectedType == String.class) {
          parsedSettings.stringSettings.put(sr.name, (String) sr.defaultValue);
        } else if(sr.expectedType == int.class) {
          parsedSettings.intSettings.put(sr.name, (Integer) sr.defaultValue);
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
  
  public static class Settings {
    
    private static class Setting<T> {
      
      private final String name;
      private final T defaultValue;
      private final Class<T> expectedType;
      
      private Setting(String name, Class<T> expectedType, T defaultValue){
        this.name = name;
        this.defaultValue = defaultValue;
        this.expectedType = expectedType;
      }
      
      @Override
      public int hashCode() {
        return name.hashCode();
      }
      
      @Override
      public boolean equals(Object o) {
        return o instanceof Setting<?> s && s.name.equals(name);
      }
    }
    private final Map<String, String> stringSettings = new HashMap<>();
    private final Map<String, Integer> intSettings = new HashMap<>();
    
    private Settings() {
    }
    
    public String getStr(String name) {
      return Optional.ofNullable(stringSettings.get(name))
                     .orElseThrow(() -> new IllegalArgumentException(STR."String setting \"\{name}\" not found"));
    }
    
    public int getInt(String name) {
      return Optional.ofNullable(intSettings.get(name))
                     .orElseThrow(() -> new IllegalArgumentException(STR."Int setting \"\{name}\" not found"));
    }
    
    public boolean getBool(String name) {
      return Boolean.parseBoolean(getStr(name));
    }
    
    /**
     * Retrieve the default download path depending on the OS
     * @return the default download path
     */
    public static String defaultDownloadPath() {
      String home;
      if (System.getProperty("os.name").toLowerCase().contains("windows")) {
        home = System.getenv("USERPROFILE");
      } else {
        home = System.getProperty("user.home");
      }
      var downloadsDir = new File(home, "Downloads");
      return downloadsDir.getAbsolutePath();
    }
  }
  
  
}