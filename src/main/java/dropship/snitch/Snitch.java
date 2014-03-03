package dropship.snitch;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import dropship.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class Snitch {

  private final Logger logger;
  private final ServiceManager snitches;

  @Inject
  Snitch(Logger logger, List<SnitchService> snitches) {
    this.logger = checkNotNull(logger, "logger");
    this.snitches = new ServiceManager(snitches);

    this.snitches.addListener(new ServiceManager.Listener() {
      @Override
      public void healthy() {
      }

      @Override
      public void stopped() {
      }

      @Override
      public void failure(Service service) {
        Snitch.this.logger.warn("Snitch service failed: %s, cause: %s", service, Throwables.getStackTraceAsString(service.failureCause()));
      }
    }, MoreExecutors.sameThreadExecutor());

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          Snitch.this.snitches.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          // ignored
        }
      }
    });
  }

  public void start() {
    logger.info("Starting snitch");
    snitches.startAsync();
  }
}
