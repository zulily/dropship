package dropship;

import com.google.common.collect.Iterables;
import dagger.ObjectGraph;
import dropship.logging.Logger;
import dropship.snitch.Snitch;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Dropship {

  public static void main(String[] args) throws Exception {
    ObjectGraph.create(new DropshipModule(args)).get(Dropship.class).run();
  }

  private final Settings settings;
  private final Logger logger;
  private final Snitch snitch;
  private final ClassLoaderService classloaderService;

  @Inject
  Dropship(Settings settings, ClassLoaderService classloaderService, Logger logger, Snitch snitch) {
    this.settings = checkNotNull(settings, "settings");
    this.classloaderService = checkNotNull(classloaderService, "class loader service");
    this.logger = checkNotNull(logger, "logger");
    this.snitch = checkNotNull(snitch, "snitch");
  }

  private void run() throws Exception {
    logger.info("Starting Dropship v%s", settings.dropshipVersion());

    URLClassLoader loader = classloaderService.getClassLoader();

    if (loader == null) {
      logger.warn("Could not create class loader; shutting down");
      System.exit(1);
    }

    logger.info("Loading main class %s", settings.mainClassName());

    Class<?> mainClass = loader.loadClass(settings.mainClassName());

    Thread.currentThread().setContextClassLoader(loader);

    Method mainMethod = mainClass.getMethod("main", String[].class);

    logger.info("Invoking main method of %s", mainClass.getName());

    System.setProperty("dropship.running", "true");

    snitch.start();

    mainMethod.invoke(null, (Object) Iterables.toArray(settings.commandLineArguments(), String.class));
  }
}
