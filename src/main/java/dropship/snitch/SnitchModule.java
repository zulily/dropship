package dropship.snitch;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;

import java.util.List;

@Module(library = true, complete = false)
public class SnitchModule {

  @Provides
  List<SnitchService> provideSnitchServices(SnitchService.GarbageCollectionSnitch gc,
                                            SnitchService.ClassLoadingSnitch cl,
                                            SnitchService.DiskSpaceSnitch disk,
                                            SnitchService.MemorySnitch mem,
                                            SnitchService.ThreadSnitch thread,
                                            SnitchService.UptimeSnitch uptime) {

    return ImmutableList.of(gc, cl, disk, mem, thread, uptime);
  }
}
