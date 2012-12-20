package com.github.smreed.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import static com.github.smreed.classloader.NotGuava.propagate;
import static com.github.smreed.classloader.NotLogger.debug;
import static com.github.smreed.classloader.NotLogger.warn;

class Settings {

  private static final String DEFAULT_CONFIG_FILE_NAME = "bootstrap.properties";
  private static final Properties CACHE = new Properties();

  private static volatile boolean loaded = false;

  static String mavenRepoUrl() {
    return loadBootstrapPropertiesUnchecked().getProperty("repo.remote.url");
  }

  static String localRepoPath() {
    return loadBootstrapPropertiesUnchecked().getProperty("repo.local.path", ".m2/repository");
  }

  static synchronized Properties loadBootstrapProperties() throws IOException {
    if (loaded) {
      return CACHE;
    }

    URL url = Bootstrap.class.getClassLoader().getResource(DEFAULT_CONFIG_FILE_NAME);
    if (url == null) {
      warn("No bootstrap.properties found! Dynamic artifact version resolution will not work.");
    } else {
      debug("Loading configuration from %s.", url);
      CACHE.load(Bootstrap.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE_NAME));
      for (Map.Entry<Object, Object> entry : CACHE.entrySet()) {
        debug("%s: %s = %s", DEFAULT_CONFIG_FILE_NAME, entry.getKey(), entry.getValue());
      }
    }
    loaded = true;
    return CACHE;
  }

  static Properties loadBootstrapPropertiesUnchecked() {
    try {
      return loadBootstrapProperties();
    } catch (Exception e) {
      throw propagate(e);
    }
  }
}
