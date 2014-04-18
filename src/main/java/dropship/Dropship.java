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

import com.google.common.collect.Iterables;
import dagger.ObjectGraph;
import dropship.logging.Logger;
import dropship.snitch.Snitch;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

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
    Dropship dropship = ObjectGraph.create(new DropshipModule(args)).get(Dropship.class);
    try {
      dropship.run();
    } catch (DropshipRuntimeException e) {
      dropship.logger.warn(e.getMessage());
      System.exit(1);
    }
  }

  private final Settings settings;
  private final Logger logger;
  private final Snitch snitch;
  private final ArtifactResolutionService artifactResolutionService;

  @Inject
  Dropship(Settings settings, ArtifactResolutionService artifactResolutionService, Logger logger, Snitch snitch) {
    this.settings = checkNotNull(settings, "settings");
    this.artifactResolutionService = checkNotNull(artifactResolutionService, "artifact resolution service");
    this.logger = checkNotNull(logger, "logger");
    this.snitch = checkNotNull(snitch, "snitch");
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

    snitch.start();

    try {
      String[] args = Iterables.toArray(settings.commandLineArguments(), String.class);
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
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        onError(e);
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

  private void preRun(Properties properties,
                      String groupArtifactString,
                      Class<?> mainClass,
                      Method mainMethod,
                      Object[] arguments) {

    // Here for agents
  }

  private void onError(Throwable e) {

    // Here for agents
  }

  private void onExit() {

    // Here for agents
  }
}
