package dropship;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Dropship {

  @Module(injects = Dropship.class, includes = {CommandLineArgs.ArgsModule.class, StatsdStatsLogger.StatsModule.class})
  static final class DropshipModule {

    private final String[] args;

    private DropshipModule(String[] args) {
      this.args = checkNotNull(args, "args");
    }

    @Provides
    @Named("args")
    String[] provideArgs() {
      return this.args;
    }

    @Provides
    @Named("jvmName")
    String provideJvmName() {
      return ManagementFactory.getRuntimeMXBean().getName();
    }

    @Provides
    SimpleDateFormat provideDateFormat() {
      return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
    }

    @Provides
    PrintStream provideLoggerDestination() {
      return System.err;
    }

    @Provides
    Logger provideLogger(TerseLogger terse, VerboseLogger verbose) {
      return "false".equals(System.getProperty("verbose", "false"))
        ? terse
        : verbose;
    }

    @Provides
    List<SnitchService> provideSnitchServices(SnitchService.GarbageCollectionSnitch gc,
                                              SnitchService.ClassLoadingSnitch cl,
                                              SnitchService.DiskSpaceSnitch disk,
                                              SnitchService.MemorySnitch mem,
                                              SnitchService.ThreadSnitch thread,
                                              SnitchService.UptimeSnitch uptime) {

      return ImmutableList.of(gc, cl, disk, mem, thread, uptime);
    }

    @Provides
    MavenClassLoader.ClassLoaderBuilder provideClassloaderBuilder(Settings settings, Logger logger) {
      Optional<String> override = settings.mavenRepoUrl();
      if (override.isPresent()) {
        logger.info("Will load artifacts from %s", override);
        return MavenClassLoader.using(settings, logger, override.get());
      } else {
        logger.info("Loading artifacts from maven central repo");
        return MavenClassLoader.usingCentralRepo(settings, logger);
      }
    }

  }

  public static void main(String[] args) throws Exception {
    ObjectGraph.create(new DropshipModule(args)).get(Dropship.class).run();
  }

  private final CommandLineArgs args;
  private final Settings settings;
  private final Logger logger;
  private final Snitch snitch;
  private final ClassLoaderService classloaderService;

  @Inject
  Dropship(CommandLineArgs args, Settings settings, ClassLoaderService classloaderService, Logger logger, Snitch snitch) {
    this.args = checkNotNull(args, "args");
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

    System.setProperty("dropship.running", "true");

    logger.info("Loading main class %s", args.mainClassName());

    Class<?> mainClass = loader.loadClass(args.mainClassName());

    Thread.currentThread().setContextClassLoader(loader);

    Method mainMethod = mainClass.getMethod("main", String[].class);

    Iterable<String> mainArgs = args.commandLineArguments();

    logger.info("Invoking main method of %s", mainClass.getName());

    snitch.start();

    mainMethod.invoke(null, (Object) Iterables.toArray(mainArgs, String.class));
  }
}
