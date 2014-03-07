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
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Dropship logger, which wraps a {@link java.io.PrintStream} in lieu of
 * any type of logging framework.
 */
public abstract class Logger {

  protected abstract PrintStream destination();

  /**
   * Returns a repository listener which will receive artifact resolution
   * progress.
   */
  public abstract AbstractRepositoryListener listener();

  protected abstract Object format(Date date, long tid, String level, String line);

  /**
   * If debug logging is enabled, formats a message with the given format
   * string and arguments using {@link String#format(String, Object...)}
   * and writes the result as debug to the log.
   *
   * @param format message format
   * @param arg first message format argument
   * @param otherArgs remaining message format arguments
   */
  public void debug(String format, Object arg, Object... otherArgs) {
    debug(format(format, arg, otherArgs));
  }

  /**
   * If debug logging is enabled, writes the given string as debug to
   * the log.
   *
   * @param message message to write
   */
  public void debug(String message) {
    write("DEBUG", message);
  }

  /**
   * If info logging is enabled, formats a message with the given format
   * string and arguments using {@link String#format(String, Object...)}
   * and writes the result as info to the log.
   *
   * @param format message format
   * @param arg first message format argument
   * @param otherArgs remaining message format arguments
   */
  public void info(String format, Object arg, Object... otherArgs) {
    info(format(format, arg, otherArgs));
  }

  /**
   * If info logging is enabled, writes the given string as info to
   * the log.
   *
   * @param message message to write
   */
  public void info(String message) {
    write(" INFO", message);
  }

  /**
   * Formats a message with the given format string and arguments using
   * {@link String#format(String, Object...)} and writes the result as
   * a warning to the log.
   *
   * @param format message format
   * @param arg first message format argument
   * @param otherArgs remaining message format arguments
   */
  public final void warn(String format, Object arg, Object... otherArgs) {
    warn(format(format, arg, otherArgs));
  }

  /**
   * Writes the given string as a warning to the log.
   *
   * @param message message to write
   */
  public final void warn(String message) {
    write(" WARN", message);
  }

  /**
   * Writes the given string as a warning to the log and prints
   * the exception stack trace.
   *
   * @param e exception to log
   * @param message message to write
   */
  public final void warn(Throwable e, String message) {
    warn(message);
    e.printStackTrace(destination());
  }

  private synchronized void write(String level, String line) {
    final long tid = Thread.currentThread().getId();
    destination().println(format(new Date(System.currentTimeMillis()), tid, level, line));
  }

  private String format(String format, Object arg, Object... otherArgs) {
    checkNotNull(format);
    checkNotNull(arg);
    checkNotNull(otherArgs);

    if (otherArgs.length > 0) {
      Object[] formatArgs = new Object[otherArgs.length + 1];
      formatArgs[0] = arg;
      System.arraycopy(otherArgs, 0, formatArgs, 1, otherArgs.length);
      return String.format(format, formatArgs);
    } else {
      return String.format(format, arg);
    }
  }
}
