package com.github.smreed.classloader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import static com.github.smreed.classloader.NotGuava.checkArgument;
import static com.github.smreed.classloader.NotGuava.checkNotNull;
import static com.github.smreed.classloader.NotGuava.propagate;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class Bootstrap {

  private static MavenClassLoader.ClassLoaderBuilder classLoaderBuilder() {
    String override = System.getProperty("maven.repo");
    if (override != null) {
      return MavenClassLoader.using(override);
    } else {
      return MavenClassLoader.usingCentralRepo();
    }
  }

  private static Properties loadBootstrapProperties() throws IOException {
    Properties properties = new Properties();
    URL url = Bootstrap.class.getClassLoader().getResource("bootstrap.properties");
    if (url == null) {
      System.err.println("No bootstrap.properties found! Dynamic artifact version resolution will not work.");
    } else {
      properties.load(Bootstrap.class.getClassLoader().getResourceAsStream("bootstrap.properties"));
    }
    return properties;
  }

  private static Properties loadBootstrapPropertiesUnchecked() {
    try {
      return loadBootstrapProperties();
    } catch (Exception e) {
      throw propagate(e);
    }
  }

  private static String resolveGav(String gav, Properties properties) {
    if (gav.split(":").length > 2) {
      return gav;
    } else if (properties.containsKey(gav)) {
      return gav + ':' + properties.getProperty(gav);
    } else {
      return gav + ":[0,)";
    }
  }

  public static void main(String[] args) throws Exception {
    args = checkNotNull(args);
    checkArgument(args.length >= 2, "Must specify groupId:artifactId[:version] and classname!");

    String gav = resolveGav(args[0], loadBootstrapPropertiesUnchecked());

    System.out.format("Requested %s, will load artifact and dependencies for %s.%n", args[0], gav);

    URLClassLoader loader = classLoaderBuilder().forGAV(gav);

    Class<?> mainClass = loader.loadClass(args[1]);

    Method mainMethod = mainClass.getMethod("main", String[].class);

    String[] mainArgs = new String[args.length - 2];
    System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);

    mainMethod.invoke(null, (Object) mainArgs);
  }
}
