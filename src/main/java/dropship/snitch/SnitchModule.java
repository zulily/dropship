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
