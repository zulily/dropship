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

import org.sonatype.aether.AbstractRepositoryListener;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
final class VerboseLogger extends Logger {

  private final LoggingRepositoryListener listener;
  private final SimpleDateFormat dateFormat;
  private final String jvmName;
  private final PrintStream destination;


  @Inject
  VerboseLogger(SimpleDateFormat dateFormat,
                @Named("jvm-name") String jvmName,
                PrintStream destination) {

    this.listener = new LoggingRepositoryListener(this);
    this.dateFormat = checkNotNull(dateFormat, "date format");
    this.jvmName = checkNotNull(jvmName, "jvm name");
    this.destination = checkNotNull(destination, "destination");
  }

  @Override
  protected PrintStream destination() {
    return destination;
  }

  /**
   * Returns a repository listener that prints message on artifact resolution
   * and download.
   */
  @Override
  public AbstractRepositoryListener listener() {
    return listener;
  }

  @Override
  protected synchronized Object format(Date date, long tid, String level, String line) {
    final String timestamp = dateFormat.format(date);
    return String.format("%s %s %2d [Dropship %s] %s", timestamp, jvmName, tid, level, line);
  }
}
