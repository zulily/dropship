package dropship;

import java.io.PrintStream;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;


abstract class Logger {

  protected abstract PrintStream destination();

  protected abstract Object format(Date date, long tid, String level, String line);

  void debug(String format, Object arg, Object... otherArgs) {
    debug(format(format, arg, otherArgs));
  }

  void debug(String message) {
    write("DEBUG", message);
  }

  void info(String format, Object arg, Object... otherArgs) {
    info(format(format, arg, otherArgs));
  }

  void info(String message) {
    write(" INFO", message);
  }

  final void warn(String format, Object arg, Object... otherArgs) {
    warn(format(format, arg, otherArgs));
  }

  final void warn(String message) {
    write(" WARN", message);
  }

  final void warn(Throwable e, String message) {
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
