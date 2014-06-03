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

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import static dropship.Preconditions.checkNotNull;

final class DropshipModule {

  private final List<String> args;

  DropshipModule(String[] args) {
    checkNotNull(args, "args");
    this.args = Arrays.asList(args);
  }

  List<String> provideArgs() {
    return args;
  }

  String provideJvmName() {
    return ManagementFactory.getRuntimeMXBean().getName();
  }

  SimpleDateFormat provideDateFormat() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
  }

  PrintStream provideLoggerDestination() {
    return System.err;
  }

  MavenArtifactResolution.ArtifactResolutionBuilder provideArtifactResolutionBuilder(Settings settings, Logger logger) {
    String override = settings.mavenRepoUrl();
    if (override != null) {
      logger.info("Will load artifacts from %s", override);
      return MavenArtifactResolution.using(settings, logger, override);
    } else {
      logger.info("Loading artifacts from maven central repo");
      return MavenArtifactResolution.usingCentralRepo(settings, logger);
    }
  }

}
