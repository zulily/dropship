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

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.artifact.Artifact;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

class LoggingRepositoryListener extends AbstractRepositoryListener {

  private final Map<String, Long> startTimes = Maps.newHashMap();
  private final Logger logger;

  LoggingRepositoryListener(Logger logger) {
    this.logger = checkNotNull(logger, "logger");
  }

  private String artifactAsString(Artifact artifact) {
    return Joiner.on(':').join(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  /**
   * {@inheritDoc}
   * @param event {@inheritDoc}
   */
  @Override
  public void artifactDownloading(RepositoryEvent event) {
    super.artifactDownloading(event);
    Artifact artifact = event.getArtifact();
    String key = artifactAsString(artifact);
    startTimes.put(key, System.nanoTime());
  }

  /**
   * {@inheritDoc}
   * @param event {@inheritDoc}
   */
  @Override
  public void artifactDownloaded(RepositoryEvent event) {
    super.artifactDownloaded(event);
    Artifact artifact = event.getArtifact();
    String key = artifactAsString(artifact);
    long downloadTimeNanos = System.nanoTime() - startTimes.remove(key);
    double downloadTimeMs = TimeUnit.NANOSECONDS.toMillis(downloadTimeNanos);
    long size = artifact.getFile().length();
    double sizeKb = (1D / 1024D) * size;
    double downloadRateKBytesPerMs = sizeKb / downloadTimeMs;
    logger.info("Downloaded %s (%d bytes) in %gms (%g KBytes/sec)", key, size, downloadTimeMs, downloadRateKBytesPerMs * 1000);
  }

  /**
   * {@inheritDoc}
   * @param event {@inheritDoc}
   */
  @Override
  public void artifactResolved(RepositoryEvent event) {
    super.artifactResolved(event);
    logger.debug("Resolved %s", artifactAsString(event.getArtifact()));
  }
}
