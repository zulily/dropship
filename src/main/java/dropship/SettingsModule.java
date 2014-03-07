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

import dagger.Module;
import dagger.Provides;
import dropship.logging.Logger;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Module(library = true, complete = false)
class SettingsModule {

  @Provides
  @Singleton
  Settings provideSettings(Logger logger, @Named("args") String[] args) {
    checkNotNull(args, "args");
    checkArgument(args.length > 0, "Must specify groupId:artifactId[:version] and classname or valid alias!");

    // If the first argument contains a ':', we will assume that Dropship is being used in
    // the original group:artifact[:version] classname mode.
    if (args[0].contains(":")) {
      return new Settings.ExplicitArtifactArguments(logger, args);
    } else {
      return new Settings.AliasArguments(logger, args);
    }
  }
}
