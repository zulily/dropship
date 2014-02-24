package dropship;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import javax.inject.Inject;
import java.net.URLClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;

final class ClassLoaderCreatorTask extends AbstractExecutionThreadService {

  private final Settings settings;
  private final CommandLineArgs args;
  private final Logger logger;
  private URLClassLoader classLoader = null;

  @Inject
  ClassLoaderCreatorTask(Settings settings, CommandLineArgs args, Logger logger) {
    this.settings = checkNotNull(settings, "settings");
    this.args = checkNotNull(args, "args");
    this.logger = checkNotNull(logger, "logger");
  }

  @Override
  protected void run() throws Exception {
    logger.info("ClassLoaderCreatorTask starting");
    this.classLoader = classLoaderBuilder().forMavenCoordinates(args.groupArtifactString());
    logger.info("ClassLoaderCreatorTask done");
  }

  public URLClassLoader getClassLoader() {
    return classLoader;
  }

  private MavenClassLoader.ClassLoaderBuilder classLoaderBuilder() {
    Optional<String> override = settings.mavenRepoUrl();
    if (override.isPresent()) {
      logger.info("Will load artifacts from %s", override);
      return MavenClassLoader.using(settings, logger, override.get());
    } else {
      return MavenClassLoader.usingCentralRepo(settings, logger);
    }
  }
}
