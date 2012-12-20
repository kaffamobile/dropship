package com.github.smreed.classloader;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.github.smreed.classloader.NotLogger.debug;
import static com.github.smreed.classloader.NotLogger.warn;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

class Settings {

  private static final String DEFAULT_CONFIG_FILE_NAME = "bootstrap.properties";
  private static final Properties CACHE = new Properties();
  private static final Splitter CSV = Splitter.on(',').trimResults().omitEmptyStrings();

  private static volatile boolean loaded = false;

  static Optional<String> mavenRepoUrl() {
    return loadProperty("repo.remote.url");
  }

  static String localRepoPath() {
    return loadProperty("repo.local.path", ".m2/repository");
  }

  static List<String> additionalClasspathPaths() {
    Optional<String> additionalClasspathPathsString = loadProperty("bootstrap.additional.paths");
    if (additionalClasspathPathsString.isPresent()) {
      return ImmutableList.copyOf(CSV.split(additionalClasspathPathsString.get()));
    } else {
      return ImmutableList.of();
    }
  }

  private static String loadProperty(String name, String defaultValue) {
    checkNotNull(defaultValue);

    return loadProperty(name).or(defaultValue);
  }

  private static Optional<String> loadProperty(String name) {
    return Optional.fromNullable(loadBootstrapPropertiesUnchecked().getProperty(name));
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
