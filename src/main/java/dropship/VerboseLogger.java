package dropship;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
class VerboseLogger extends Logger {

  private final SimpleDateFormat dateFormat;
  private final String jvmName;
  private final PrintStream destination;

  @Inject
  VerboseLogger(SimpleDateFormat dateFormat,
                @Named("jvmName") String jvmName,
                PrintStream destination) {

    this.dateFormat = checkNotNull(dateFormat, "dateFormat");
    this.jvmName = checkNotNull(jvmName, "jvmName");
    this.destination = checkNotNull(destination, "destination");
  }

  @Override
  protected PrintStream destination() {
    return destination;
  }

  @Override
  protected synchronized Object format(Date date, long tid, String level, String line) {
    final String timestamp = dateFormat.format(date);
    return String.format("%s %s %2d [Dropship %s] %s", timestamp, jvmName, tid, level, line);
  }
}
