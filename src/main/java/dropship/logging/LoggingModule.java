package dropship.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import dagger.Module;
import dagger.Provides;
import dropship.Settings;

import javax.inject.Singleton;
import java.io.PrintStream;
import java.text.SimpleDateFormat;

@Module(library = true, complete = false)
public class LoggingModule {

  @Provides
  @Singleton
  StatsdStatsLogger provideStatsLogger(Settings settings, Logger logger) {
    Optional<String> host = settings.statsdHost();
    Optional<Integer> port = settings.statsdPort();

    logger.info("Statsd configuration: host=%s, port=%s", host.or("<not set>"), port.transform(Functions.toStringFunction()).or("<not set>"));

    if (host.isPresent() && port.isPresent()) {
      return new StatsdStatsLogger.StatsdStatsLoggerImpl(settings, logger, host.get(), port.get());
    } else if (host.isPresent()) {
      return new StatsdStatsLogger.StatsdStatsLoggerImpl(settings, logger, host.get());
    } else {
      return new StatsdStatsLogger.NoopLogger(settings);
    }
  }

  @Provides @VisibleForTesting
  public TerseLogger provideTerseLogger(SimpleDateFormat format, PrintStream destination) {
    return new TerseLogger(format, destination);
  }

  @Provides
  Logger provideLogger(TerseLogger terse, VerboseLogger verbose) {
    return "false".equals(System.getProperty("verbose", "false"))
      ? terse
      : verbose;
  }
}
