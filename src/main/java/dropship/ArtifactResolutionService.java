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

import java.net.URLClassLoader;

import static dropship.Preconditions.checkNotNull;
import static dropship.Preconditions.checkState;

final class ArtifactResolutionService {

  private final Settings settings;
  private final MavenArtifactResolution.ArtifactResolutionBuilder clBuilder;

  private URLClassLoader classLoader = null;

  ArtifactResolutionService(Settings settings, MavenArtifactResolution.ArtifactResolutionBuilder clBuilder) {
    this.settings = checkNotNull(settings, "settings");
    this.clBuilder = checkNotNull(clBuilder, "class loader builder");
  }

  synchronized URLClassLoader getClassLoader() {
    if (classLoader == null) {
      classLoader = clBuilder.createClassLoader(settings.groupArtifactString());
    }

    checkState(classLoader != null, "ClassLoader has not been created");
    return classLoader;
  }

  synchronized void downloadArtifacts() {
    clBuilder.downloadArtifacts(settings.groupArtifactString());
  }
}
