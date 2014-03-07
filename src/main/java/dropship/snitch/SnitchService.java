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

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dropship.Settings;
import dropship.logging.Logger;
import dropship.logging.StatsdStatsLogger;

import javax.inject.Inject;
import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class SnitchService extends AbstractScheduledService {

  private static final Joiner KEY_JOINER = Joiner.on('.').useForNull("null");

  private static String getHostname() {
    String defaultHostname = "localhost";
    try {
      return Iterables.getFirst(Splitter.on('.').split(InetAddress.getLocalHost().getHostName()), defaultHostname);
    } catch (Exception e) {
      return defaultHostname;
    }
  }

  private final ImmutableList<String> gavKeys;
  private final ImmutableList<String> hostKeys;
  private final ImmutableList<String> methodKeys;

  SnitchService(Settings settings) {
    checkNotNull(settings, "settings");
    this.gavKeys = ImmutableList.copyOf(Iterables.limit(Splitter.on(':').split(CharMatcher.is('.').replaceFrom(settings.groupArtifactString(), '-')), 2));
    this.hostKeys = ImmutableList.of(getHostname());
    String simplifiedMainClassName = Iterables.getLast(Splitter.on('.').split(settings.mainClassName()));
    this.methodKeys = ImmutableList.of(simplifiedMainClassName);
  }

  @Override
  protected final Scheduler scheduler() {
    return Scheduler.newFixedRateSchedule(0L, 1L, TimeUnit.SECONDS);
  }

  @Override
  protected final ScheduledExecutorService executor() {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat(serviceName()).setDaemon(true).build()
    );
    addListener(new Listener() {
      @Override
      public void starting() {
      }

      @Override
      public void running() {
      }

      @Override
      public void stopping(State from) {
      }

      @Override
      public void terminated(State from) {
        executor.shutdown();
      }

      @Override
      public void failed(State from, Throwable failure) {
        executor.shutdown();
      }
    }, MoreExecutors.sameThreadExecutor());
    return executor;
  }

  protected String key(String key) {
    return KEY_JOINER.join(Iterables.concat(baseKey(), ImmutableList.of(key)));
  }

  private Iterable<String> baseKey() {
    return Iterables.concat(
      ImmutableList.of("dropship"),
      hostKeys,
      gavKeys,
      methodKeys
    );
  }

  static final class GarbageCollectionSnitch extends SnitchService {

    private final StatsdStatsLogger logger;
    private final ImmutableList<GarbageCollectorMXBean> mxBeans;
    private final Map<String, Long> collectionTimes;
    private final Map<String, Long> collectionCounts;

    @Inject
    public GarbageCollectionSnitch(StatsdStatsLogger logger, Settings settings) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.mxBeans = ImmutableList.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
      this.collectionTimes = Maps.newHashMapWithExpectedSize(mxBeans.size());
      this.collectionCounts = Maps.newHashMapWithExpectedSize(mxBeans.size());
    }

    @Override
    public void runOneIteration() {
      for (GarbageCollectorMXBean mxBean : mxBeans) {
        String gcName = mxBean.getName();

        long time = mxBean.getCollectionTime();
        if (collectionTimes.containsKey(gcName)) {
          long delta = time - collectionTimes.get(gcName);
          collectionTimes.put(gcName, time);
          time = delta;
        } else {
          collectionTimes.put(gcName, time);
        }

        logger.increment(key("gc-" + gcName + "-time-ms"), time);

        long count = mxBean.getCollectionCount();
        if (collectionCounts.containsKey(gcName)) {
          long delta = count - collectionCounts.get(gcName);
          collectionCounts.put(gcName, count);
          count = delta;
        } else {
          collectionCounts.put(gcName, count);
        }

        logger.increment(key("gc-" + gcName + "-count"), count);
      }
    }
  }

  static final class ThreadSnitch extends SnitchService {

    private final StatsdStatsLogger logger;
    private final ThreadMXBean mxBean;
    private final boolean skipCpu;

    private long lastCpuTimeNanos = 0L;

    @Inject
    public ThreadSnitch(StatsdStatsLogger logger, Settings settings, Logger console) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.mxBean = ManagementFactory.getThreadMXBean();

      if (mxBean.isThreadCpuTimeSupported()) {
        mxBean.setThreadCpuTimeEnabled(true);
        skipCpu = false;
      } else {
        console.warn("Thread CPU time snitching is not supported");
        skipCpu = true;
      }
    }

    @Override
    public void runOneIteration() {
      long[] threadIds = mxBean.getAllThreadIds();

      logger.increment(key("daemon-threads"), mxBean.getDaemonThreadCount());
      logger.increment(key("peak-threads"), mxBean.getPeakThreadCount());
      logger.increment(key("all-threads"), mxBean.getThreadCount());
      logger.increment(key("total-threads"), mxBean.getTotalStartedThreadCount());

      if (skipCpu) {
        logger.increment(key("cpu-time-ms"), 0L);
      } else {
        long totalCpuTimeNanos = 0L;
        for (long thread : threadIds) {
          long threadCpuTimeNanos = mxBean.getThreadCpuTime(thread);
          if (threadCpuTimeNanos > 0) {
            totalCpuTimeNanos += threadCpuTimeNanos;
          }
        }

        long ms = TimeUnit.NANOSECONDS.toMillis(totalCpuTimeNanos - lastCpuTimeNanos);
        if (ms > 0) {
          lastCpuTimeNanos = totalCpuTimeNanos;
        }
        logger.increment(key("cpu-time-ms"), Math.max(0, ms));
      }
    }
  }

  static final class MemorySnitch extends SnitchService {

    private final StatsdStatsLogger logger;
    private final MemoryMXBean mxBean;

    @Inject
    public MemorySnitch(StatsdStatsLogger logger, Settings settings) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.mxBean = ManagementFactory.getMemoryMXBean();
    }

    @Override
    public void runOneIteration() {
      MemoryUsage heap = mxBean.getHeapMemoryUsage();
      long heapCommitted = heap.getCommitted();
      long heapInit = heap.getInit();
      long heapMax = heap.getMax();
      long heapUsed = heap.getUsed();

      MemoryUsage nonHeap = mxBean.getNonHeapMemoryUsage();
      long nonHeapCommitted = nonHeap.getCommitted();
      long nonHeapInit = nonHeap.getInit();
      long nonHeapMax = nonHeap.getMax();
      long nonHeapUsed = nonHeap.getUsed();

      long pending = mxBean.getObjectPendingFinalizationCount();

      logger.increment(key("heap-committed"), heapCommitted);
      logger.increment(key("heap-init"), heapInit);
      logger.increment(key("heap-max"), heapMax);
      logger.increment(key("heap-used"), heapUsed);

      logger.increment(key("nonheap-committed"), nonHeapCommitted);
      logger.increment(key("nonheap-init"), nonHeapInit);
      logger.increment(key("nonheap-max"), nonHeapMax);
      logger.increment(key("nonheap-used"), nonHeapUsed);

      logger.increment(key("pending-finalization"), pending);
    }
  }

  static final class ClassLoadingSnitch extends SnitchService {

    private final StatsdStatsLogger logger;
    private final ClassLoadingMXBean mxBean;

    @Inject
    public ClassLoadingSnitch(StatsdStatsLogger logger, Settings settings) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.mxBean = ManagementFactory.getClassLoadingMXBean();
    }

    @Override
    public void runOneIteration() {
      int loaded = mxBean.getLoadedClassCount();
      long totalLoaded = mxBean.getTotalLoadedClassCount();
      long unloaded = mxBean.getUnloadedClassCount();

      logger.increment(key("classes-loaded"), loaded);
      logger.increment(key("classes-loaded-total"), totalLoaded);
      logger.increment(key("classes-unloaded"), unloaded);
    }
  }

  static final class DiskSpaceSnitch extends SnitchService {

    private final StatsdStatsLogger logger;

    private final File cwd;

    @Inject
    public DiskSpaceSnitch(StatsdStatsLogger logger, Settings settings) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.cwd = new File(".");
    }

    @Override
    public void runOneIteration() {
      long totalSpace = cwd.getTotalSpace();
      long freeSpace = cwd.getFreeSpace();
      long usableSpace = cwd.getUsableSpace();

      logger.increment(key("total-disk"), totalSpace, 1D);
      logger.increment(key("usable-disk"), usableSpace, 1D);
      logger.increment(key("free-disk"), freeSpace, 1D);
    }
  }

  static final class UptimeSnitch extends SnitchService {

    private final StatsdStatsLogger logger;
    private final RuntimeMXBean mxBean;

    @Inject
    public UptimeSnitch(StatsdStatsLogger logger, Settings settings) {
      super(settings);

      this.logger = checkNotNull(logger);
      this.mxBean = ManagementFactory.getRuntimeMXBean();
    }

    @Override
    public void runOneIteration() {
      logger.increment(key("uptime-ms"), mxBean.getUptime(), 1D);
      logger.increment(key("ticks"), 1L, 1D);
    }
  }

  static class NoOp extends SnitchService {

    NoOp(Settings settings) {
      super(settings);
    }

    @Override
    protected void runOneIteration() {}
  }
}
