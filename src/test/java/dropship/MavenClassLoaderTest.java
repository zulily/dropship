package dropship;

import com.google.common.collect.Multiset;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;

import static org.fest.assertions.Assertions.assertThat;

public class MavenClassLoaderTest {

  private Settings settings;
  private VerboseLogger logger;

  @Before public void setup() {
    logger = new VerboseLogger(new SimpleDateFormat(), "test", System.err);
    settings = new Settings(logger);
  }

  @Test
  public void jodaTime() throws Exception {
    String gav = "joda-time:joda-time:[1.6,)";
    String className = "org.joda.time.chrono.BuddhistChronology";
    ClassLoader loader = MavenClassLoader.forMavenCoordinates(settings, logger, gav);
    assertThat(loader).isNotNull();
    Class<?> buddhistChronology = loader.loadClass(className);
    assertThat(buddhistChronology).isNotNull();
    Method factoryMethod = buddhistChronology.getMethod("getInstance");
    assertThat(factoryMethod.invoke(null)).isNotNull();
  }

  @Test(expected = ClassNotFoundException.class)
  public void jodaTimeClassLoaderDoesNotHaveMultiset() throws ClassNotFoundException {
    // This test verifies that, although we have access to certain classes in THIS classloader in THIS thread,
    // the classloader loaded by maven GAV does NOT.
    String gav = "joda-time:joda-time:[1.6,)";
    ClassLoader loader = MavenClassLoader.forMavenCoordinates(settings, logger, gav);
    assertThat(loader).isNotNull();
    assertThat(Thread.currentThread().getContextClassLoader().loadClass(Multiset.class.getName())).isNotNull();
    loader.loadClass(Multiset.class.getName());
  }

  @Test
  public void useContextClassloader() throws Exception {
    // v0.2 and prior did not test context class loader in Dropship, which caused problems with
    // libraries such as hadoop. This test reproduces that behavior and ensures that we can
    // set the context class loader and then use it to load classes.
    ClassLoader old = Thread.currentThread().getContextClassLoader();
    try {
      String gav = "joda-time:joda-time:[1.6,)";
      String className = "org.joda.time.chrono.BuddhistChronology";
      ClassLoader loader = MavenClassLoader.forMavenCoordinates(settings, logger, gav);
      Thread.currentThread().setContextClassLoader(loader);
      loader = Thread.currentThread().getContextClassLoader();
      assertThat(loader).isNotNull();
      Class<?> buddhistChronology = loader.loadClass(className);
      assertThat(buddhistChronology).isNotNull();
      Method factoryMethod = buddhistChronology.getMethod("getInstance");
      assertThat(factoryMethod.invoke(null)).isNotNull();
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void classLoaderConstructionFailsOnBogusGAV() {
    MavenClassLoader.forMavenCoordinates(settings, logger, "this isn't going to work!");
  }
}
