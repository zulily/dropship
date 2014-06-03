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

import java.util.ArrayList;
import java.util.List;

import static dropship.Preconditions.checkNotNull;
import static dropship.Settings.DownloadModeArguments;

class SettingsModule {

  private final static String usage;

  static {
    String line = System.lineSeparator();

    // Keep the width to 80 chars or less
    usage =
      "Usage: java -jar dropship.jar [<options>] [<GAV> <main_class>]|[<alias>] args..." + line +  line +

      "Options:" + line +
      "--help                Displays this message and exits." + line +
      "--offline             Attempts to resolve all dependencies without contacting a remote " + line +
      "                      maven repo." + line +
      "--download=local_dir  Downloads all resolved dependencies to local_dir, then" + line +
      "                      exits. Dropship will not attempt to run a main class, " + line +
      "                      so no main class argument is required." + line + line +

      "GAV: a maven [group:artifact:[version]] string. If you omit the version, " + line +
      "     Dropship will automatically run the latest version of the artifact." + line +
      "alias: an alias name from dropship.properties that specifies a maven GAV and" + line +
      "       main class" + line +
      "args: arguments to be passed through to the specified main class" + line + line +

      "Examples:" + line +
      "java -jar dropship.jar mygroup:myartifact:1.5 mygroup.myartifact.Main args..." + line +
      "java -jar dropship.jar mygroup:myartifact mygroup.myartifact.Main args..." + line +
      "java -jar dropship.jar myalias" + line +
      "java -jar dropship.jar --download=/tmp/dir/ mygroup:myartifact:1.5 " + line +
      "java -jar dropship.jar --offline mygroup:myartifact " + line +
      "java -jar dropship.jar --offline --download=/tmp/dir/ mygroup:myartifact";
  }

  Settings provideSettings(Logger logger, List<String> args) {
    checkNotNull(args, "args");

    if (args.size() == 0 || args.contains("--help")) {
      System.out.println(usage);
      System.exit(args.size() == 0 ? 1 : 0);
    }

    List<String> nonOptions = new ArrayList<String>();
    List<String> options = new ArrayList<String>();
    boolean offlineMode = false;
    boolean downloadMode = false;
    for (String arg : args) {
      if ("--offlineMode".equals(arg)) {
        offlineMode = true;
      }
      if (arg != null && arg.startsWith("--")) {
        options.add(arg);
        if (arg.startsWith("--download=")) {
          downloadMode = true;
        }
      } else {
        nonOptions.add(arg);
      }
    }

    if (nonOptions.isEmpty()) {
      System.out.println(usage);
      System.exit(1);
    }

    // If the first argument contains a ':', we will assume that Dropship is being used in
    // the original 'group:artifact[:version] classname' mode, rather than 'alias' mode
    Settings settings;
    if (nonOptions.get(0).contains(":")) {
      settings = new Settings.ExplicitArtifactArguments(logger, nonOptions, offlineMode, downloadMode);
    } else {
      settings = new Settings.AliasArguments(logger, nonOptions, offlineMode);
    }

    if (downloadMode) {
      settings = new DownloadModeArguments(logger, settings, options);
    }

    return settings;
  }
}
