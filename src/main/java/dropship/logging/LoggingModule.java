/*
 * Copyright (C) 2014 zulily, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * Dagger module that provides logging.
 */
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
      return new StatsdStatsLogger.NoOpLogger(settings);
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
