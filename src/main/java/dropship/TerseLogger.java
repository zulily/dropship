package dropship;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;


@Singleton
class TerseLogger extends Logger {

  private final SimpleDateFormat dateFormat;
  private final PrintStream destination;

  @Inject
  TerseLogger(SimpleDateFormat dateFormat,
              PrintStream destination) {

    this.dateFormat = checkNotNull(dateFormat, "dateFormat");
    this.destination = checkNotNull(destination, "destination");
  }

  @Override
  void debug(String format, Object arg, Object... otherArgs) {
  }

  @Override
  void debug(String message) {
  }

  @Override
  void info(String format, Object arg, Object... otherArgs) {
  }

  @Override
  void info(String message) {
  }

  @Override
  protected PrintStream destination() {
    return destination;
  }

  @Override
  protected synchronized Object format(Date date, long tid, String level, String line) {
    final String timestamp = dateFormat.format(date);
    return String.format("%s [Dropship %s] %s", timestamp, level, line);
  }
}
