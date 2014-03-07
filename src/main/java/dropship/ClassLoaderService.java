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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Singleton
final class ClassLoaderService {

  private final Settings settings;
  private final MavenClassLoader.ClassLoaderBuilder clBuilder;

  private URLClassLoader classLoader = null;

  @Inject
  ClassLoaderService(Settings settings, MavenClassLoader.ClassLoaderBuilder clBuilder) {
    this.settings = checkNotNull(settings, "settings");
    this.clBuilder = checkNotNull(clBuilder, "class loader builder");
  }

  synchronized URLClassLoader getClassLoader() {
    if (classLoader == null) {
      classLoader = clBuilder.forMavenCoordinates(settings.groupArtifactString());
    }

    checkState(classLoader != null, "Classloader has not been created");
    return classLoader;
  }
}
