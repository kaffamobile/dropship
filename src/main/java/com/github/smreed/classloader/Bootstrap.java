package com.github.smreed.classloader;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Properties;

import static com.github.smreed.classloader.NotGuava.checkArgument;
import static com.github.smreed.classloader.NotGuava.checkNotNull;
import static com.github.smreed.classloader.NotLogger.info;

public final class Bootstrap {

  private static MavenClassLoader.ClassLoaderBuilder classLoaderBuilder() {
    String override = Settings.mavenRepoUrl();
    if (override != null) {
      info("Will load artifacts from %s", override);
      return MavenClassLoader.using(override);
    } else {
      return MavenClassLoader.usingCentralRepo();
    }
  }

  private static String resolveGav(String gav) {
    if (gav.split(":").length > 2) {
      return gav;
    }

    Properties settings = Settings.loadBootstrapPropertiesUnchecked();

    if (settings.containsKey(gav)) {
      return gav + ':' + settings.getProperty(gav);
    } else {
      return gav + ":[0,)";
    }
  }

  public static void main(String[] args) throws Exception {
    args = checkNotNull(args);
    checkArgument(args.length >= 2, "Must specify groupId:artifactId[:version] and classname!");

    String gav = resolveGav(args[0]);

    info("Requested %s, will load artifact and dependencies for %s.", args[0], gav);

    URLClassLoader loader = classLoaderBuilder().forGAV(gav);

    Class<?> mainClass = loader.loadClass(args[1]);

    Method mainMethod = mainClass.getMethod("main", String[].class);

    String[] mainArgs = new String[args.length - 2];
    System.arraycopy(args, 2, mainArgs, 0, mainArgs.length);

    mainMethod.invoke(null, (Object) mainArgs);
  }
}
