package dropship.logging;

import org.sonatype.aether.AbstractRepositoryListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;


@Singleton
final class TerseLogger extends Logger {

  private final SimpleDateFormat dateFormat;
  private final PrintStream destination;

  @Inject
  TerseLogger(SimpleDateFormat dateFormat,
              PrintStream destination) {

    this.dateFormat = checkNotNull(dateFormat, "dateFormat");
    this.destination = checkNotNull(destination, "destination");
  }

  @Override
  public void debug(String format, Object arg, Object... otherArgs) {
  }

  @Override
  public void debug(String message) {
  }

  @Override
  public void info(String format, Object arg, Object... otherArgs) {
  }

  @Override
  public void info(String message) {
  }

  @Override
  protected PrintStream destination() {
    return destination;
  }

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
