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

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static dropship.Preconditions.checkNotNull;

final class TerseLogger extends Logger {

  private final SimpleDateFormat dateFormat;
  private final PrintStream destination;

  TerseLogger(SimpleDateFormat dateFormat,
              PrintStream destination) {

    this.dateFormat = checkNotNull(dateFormat, "dateFormat");
    this.destination = checkNotNull(destination, "destination");
  }

  /**
   * Ignores all parameters, does nothing. Debug is disabled in this logger.
   * @param format ignored
   * @param arg ignored
   * @param otherArgs ignored
   */
  @Override
  public void debug(String format, Object arg, Object... otherArgs) {
  }

  /**
   * Ignores all parameters, does nothing. Debug is disabled in this logger.
   * @param ignored ignored
   */
  @Override
  public void debug(String ignored) {
  }

  /**
   * Ignores all parameters, does nothing. Info is disabled in this logger.
   * @param format ignored
   * @param arg ignored
   * @param otherArgs ignored
   */
  @Override
  public void info(String format, Object arg, Object... otherArgs) {
  }

  /**
   * Ignores all parameters, does nothing. Info is disabled in this logger.
   * @param message message to write
   */
  @Override
  public void info(String message) {
  }

  @Override
  protected PrintStream destination() {
    return destination;
  }

  /**
   * Returns a repository listener with no overrides.
   */
  @Override
  public AbstractRepositoryListener listener() {
    return new AbstractRepositoryListener() {};
  }

  @Override
  protected synchronized Object format(Date date, long tid, String level, String line) {
    final String timestamp = dateFormat.format(date);
    return String.format("%s [Dropship %s] %s", timestamp, level, line);
  }
}
