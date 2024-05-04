package fr.uge.chadow;

import fr.uge.chadow.client.cli.display.View;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Settings {

  /**
   * Generate a random login starting with "user" and followed by the 7 first characters of a sha1 hash
   * of multiple variables (current time, random number, etc.)
   *
   * @return a random generated login
   */
  public static String randomLogin() {
    var login = "user";
    // digest the env vars with a loop
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-1");
      digest.update(String.valueOf(System.currentTimeMillis()).getBytes());
      Map<String, String> env = System.getenv();
      for (var entry : env.entrySet()) {
        digest.update(entry.getValue().getBytes());
      }
      var hash = View.bytesToHexadecimal(digest.digest());
      return login + hash.substring(0, 7);
    } catch (NoSuchAlgorithmException e) {
      Logger.getLogger(Settings.class.getName()).severe("SHA-1 algorithm not found");
    }
    var random = new Random();
    return login + random.nextInt(10000);
  }

  public void addStringSettings(String name, String s) {
    stringSettings.put(name, s);
  }

  public void addIntSettings(String name, Integer integer) {
    intSettings.put(name, integer);
  }

  public void addBooleanSettings(String name, Boolean aBoolean) {
    stringSettings.put(name, aBoolean.toString());
  }

  public static class Setting<T> {

    private final String name;
    private final T defaultValue;
    final Class<T> expectedType;

    Setting(String name, Class<T> expectedType, T defaultValue) {
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

    public Class<T> expectedType() {
      return expectedType;
    }

    public String name() {
      return name;
    }

    public T defaultValue() {
      return defaultValue;
    }
  }

  private final Map<String, String> stringSettings = new HashMap<>();
  private final Map<String, Integer> intSettings = new HashMap<>();

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
   *
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

  public void addSetting(Class<?> expectedType, String name, String value) throws IOException {
    if (expectedType == String.class) {
      stringSettings.put(name, value);
    } else if (expectedType == int.class) {
      int intValue;
      try {
        intValue = Integer.parseInt(value);
      } catch (NumberFormatException e) {
        throw new IOException(STR."Invalid integer value \"\{value}\" for setting \"\{name}\"");
      }
      intSettings.put(name, intValue);
    } else if (expectedType == boolean.class) {
      if (!Pattern.compile("true|false|1|0").matcher(value).matches()) {
        throw new IOException(STR."Invalid boolean value \"\{value}\" for setting \"\{name}\"");
      }
      stringSettings.put(name, value);
    } else {
      throw new AssertionError("Unexpected setting type");
    }
  }
}