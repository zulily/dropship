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
package dropship;

import dropship.logging.Logger;
import dropship.logging.LoggingModule;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

import static dropship.Preconditions.checkNotNull;

/**
 * Dropship main class. This class contains the entry point when dropship is
 * run as an executable jar.
 */
public final class Dropship {

  /**
   * Dropship command-line interface. Arguments specify an artifact by
   * group ID, artifact ID, and an optional version. This specification is
   * either explicit, using {@code group:artifact[:version]} syntax,
   * or implicit via an alias.
   */
  public static void main(String[] args) throws Exception {
    DropshipModule module = new DropshipModule(args);
    LoggingModule logging = new LoggingModule();
    SettingsModule settingsModule = new SettingsModule();

    Logger logger = logging.provideLogger(module.provideDateFormat(), module.provideJvmName(), module.provideLoggerDestination());
    Settings settings = settingsModule.provideSettings(logger, module.provideArgs());
    ArtifactResolutionService artifactService = new ArtifactResolutionService(settings, module.provideArtifactResolutionBuilder(settings, logger));

    Dropship dropship = new Dropship(
      settings,
      artifactService,
      logger
    );

    try {
      dropship.run();
    } catch (DropshipRuntimeException e) {
      dropship.logger.warn(e.getMessage());
      System.exit(1);
    }
  }

  private final Settings settings;
  private final Logger logger;
  private final ArtifactResolutionService artifactResolutionService;

  Dropship(Settings settings, ArtifactResolutionService artifactResolutionService, Logger logger) {
    this.settings = checkNotNull(settings, "settings");
    this.artifactResolutionService = checkNotNull(artifactResolutionService, "artifact resolution service");
    this.logger = checkNotNull(logger, "logger");
  }

  private void run() throws Exception {
    logger.info("Starting Dropship v%s", settings.dropshipVersion());

    if (settings.downloadMode()) {
      artifactResolutionService.downloadArtifacts();
      // download mode doesn't build a classloader, exit w/ 0
      System.exit(0);
    }

    URLClassLoader loader = artifactResolutionService.getClassLoader();

   if (loader == null) {
      logger.warn("Could not create class loader; shutting down");
      System.exit(1);
    }

    setupThreadDefaults(loader);
    setupExitHook();

    logger.info("Loading main class %s", settings.mainClassName());

    Class<?> mainClass = loader.loadClass(settings.mainClassName());

    Method mainMethod = mainClass.getMethod("main", String[].class);

    try {
      List<String> commandLineArguments = settings.commandLineArguments();
      String[] args = commandLineArguments.toArray(new String[commandLineArguments.size()]);
      preRun(settings.asProperties(), settings.groupArtifactString(), mainClass, mainMethod, args);

      logger.info("Invoking main method of %s", mainClass.getName());
      System.setProperty("dropship.running", "true");
      mainMethod.invoke(null, (Object) args);
    } catch (Exception e) {
      onError(e);
    }
  }

  private void setupThreadDefaults(ClassLoader loader) {
    Thread.currentThread().setContextClassLoader(loader);
    final Thread.UncaughtExceptionHandler priorHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        onError(e);
        if (priorHandler != null) {
          priorHandler.uncaughtException(t, e);
        } else {
          e.printStackTrace();
        }
      }
    });
  }

  private void setupExitHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        onExit();
      }
    }));
  }

  @SuppressWarnings("UnusedParameters")
  private void preRun(Properties properties,
                      String groupArtifactString,
                      Class<?> mainClass,
                      Method mainMethod,
                      Object[] arguments) {

    // Here for agents
  }

  @SuppressWarnings("UnusedParameters")
  private void onError(Throwable e) {

    // Here for agents
  }

  private void onExit() {

    // Here for agents
  }
}
