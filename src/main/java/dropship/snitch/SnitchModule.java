package dropship.snitch;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import dropship.Settings;

import java.util.List;

/**
 * Dagger module that provides a list of snitch services to run.
 */
@Module(library = true, complete = false)
public class SnitchModule {

  @Provides
  List<SnitchService> provideSnitchServices(Settings settings,
                                            SnitchService.GarbageCollectionSnitch gc,
                                            SnitchService.ClassLoadingSnitch cl,
                                            SnitchService.DiskSpaceSnitch disk,
                                            SnitchService.MemorySnitch mem,
                                            SnitchService.ThreadSnitch thread,
                                            SnitchService.UptimeSnitch uptime) {

    Optional<String> host = settings.statsdHost();

    if (!host.isPresent()) {
      return ImmutableList.<SnitchService>of(new SnitchService.NoOp(settings));
    }

    return ImmutableList.of(gc, cl, disk, mem, thread, uptime);
  }
}
