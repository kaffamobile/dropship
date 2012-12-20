package com.github.smreed.classloader;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.fest.assertions.Assertions.assertThat;

public class MavenClassLoaderTest {

  @Test
  public void jodaTime() throws Exception {
    String gav = "joda-time:joda-time:[1.6,)";
    String className = "org.joda.time.chrono.BuddhistChronology";
    ClassLoader loader = MavenClassLoader.forGAV(gav);
    assertThat(loader).isNotNull();
    Class<?> buddhistChronology = loader.loadClass(className);
    assertThat(buddhistChronology).isNotNull();
    Method factoryMethod = buddhistChronology.getMethod("getInstance");
    assertThat(factoryMethod.invoke(null)).isNotNull();
  }

  @Test(expected = ClassNotFoundException.class)
  public void jodaTimeClassLoaderDoesNotHaveMultiset() throws ClassNotFoundException {
    String gav = "joda-time:joda-time:[1.6,)";
    ClassLoader loader = MavenClassLoader.forGAV(gav);
    assertThat(loader).isNotNull();
    loader.loadClass("com.google.common.collect.Multiset");
  }

  @Test
  public void useContextClassloader() throws Exception {
    String gav = "joda-time:joda-time:[1.6,)";
    String className = "org.joda.time.chrono.BuddhistChronology";
    ClassLoader loader = MavenClassLoader.forGAV(gav);
    Thread.currentThread().setContextClassLoader(loader);
    loader = Thread.currentThread().getContextClassLoader();
    assertThat(loader).isNotNull();
    Class<?> buddhistChronology = loader.loadClass(className);
    assertThat(buddhistChronology).isNotNull();
    Method factoryMethod = buddhistChronology.getMethod("getInstance");
    assertThat(factoryMethod.invoke(null)).isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void classLoaderConstructionFailsOnBogusGAV() {
    MavenClassLoader.forGAV("this isn't going to work!");
  }
}
