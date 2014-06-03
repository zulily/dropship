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
package dropship;

import dropship.logging.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.VersionRangeResolutionException;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.filter.ScopeDependencyFilter;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dropship.Preconditions.checkArgument;
import static dropship.Preconditions.checkNotNull;

final class MavenArtifactResolution {

  static class ArtifactResolutionBuilder {

    private static final String COMPILE_SCOPE = "compile";
    private static final ClassLoader SHARE_NOTHING = null;

    private final Settings settings;
    private final Logger logger;
    private final List<RemoteRepository> repositories;
    private final File localRepositoryDirectory;

    private ArtifactResolutionBuilder(Settings settings, Logger logger, RemoteRepository... repositories) {
      this.settings = checkNotNull(settings, "settings");
      this.logger = checkNotNull(logger, "logger");
      checkNotNull(repositories, "repositories");
      checkArgument(repositories.length > 0, "Must specify at least one remote repository.");

      this.repositories = Arrays.asList(repositories);
      this.localRepositoryDirectory = new File(settings.localRepoPath());
    }

    /**
     * Downloads all artifacts for resolved dependencies to a directory specified in the {@link dropship.Settings}.
     *
     * @param gav the group:artifact:version to resolve against, i.e. joda-time:joda-time:1.6.2
     */
    public void downloadArtifacts(String gav) {
      try {
        CollectRequest collectRequest = createCollectRequestForGAV(gav);
        downloadArtifacts(collectRequest);
      } catch (ArtifactNotFoundException e) {
        throw new DropshipRuntimeException(e.getMessage());
      } catch (VersionRangeResolutionException e) {
        throw new DropshipRuntimeException(e.getMessage());
      }
    }

    private void downloadArtifacts(CollectRequest request)
      throws VersionRangeResolutionException, ArtifactNotFoundException {
      try {
        logger.info("Resolving dependencies");
        List<Artifact> artifacts = collectDependenciesIntoArtifacts(request);

        final File downloadDir = new File(settings.localDownloadPath());

        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
          throw new DropshipRuntimeException("Could not create the local download directory " + settings.localDownloadPath());
        }

        for (Artifact artifact : artifacts) {
          logger.info("Copying " + artifact.getFile().getName() + " to " + settings.localDownloadPath());
          copy(artifact.getFile(), new File(downloadDir, artifact.getFile().getName()));
        }

      } catch (Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
          rootCause = rootCause.getCause();
        }
        if (rootCause instanceof VersionRangeResolutionException) {
          throw ((VersionRangeResolutionException) rootCause);
        }
        if (rootCause instanceof ArtifactNotFoundException) {
          throw ((ArtifactNotFoundException) rootCause);
        }
        if (e instanceof SecurityException) {
          throw ((SecurityException) e);
        }
        if (e instanceof DropshipRuntimeException) {
          throw ((DropshipRuntimeException) e);
        }
        if (e instanceof RuntimeException) {
          throw ((RuntimeException) e);
        }
        throw new RuntimeException(e);
      }
    }

    private void copy(File source, File destination) throws IOException {
      if (!destination.exists()) {
        if (!destination.createNewFile()) {
          throw new RuntimeException("Could not create destination file: " + destination.getAbsolutePath());
        }
      }

      FileChannel sourceChannel = null;
      FileChannel destinationChannel = null;
      try {
        sourceChannel = new FileInputStream(source).getChannel();
        destinationChannel = new FileOutputStream(destination).getChannel();
        destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
      } finally {
        if (sourceChannel != null) {
          sourceChannel.close();
        }
        if (destinationChannel != null) {
          destinationChannel.close();
        }
      }
    }

    public URLClassLoader createClassLoader(String groupArtifactVersion) {
      try {
        CollectRequest collectRequest = createCollectRequestForGAV(groupArtifactVersion);
        return this.createClassLoader(groupArtifactVersion, collectRequest);
      } catch (ArtifactNotFoundException e) {
        throw new DropshipRuntimeException(e.getMessage());
      } catch (VersionRangeResolutionException e) {
        throw new DropshipRuntimeException(e.getMessage());
      }
    }

    private URLClassLoader createClassLoader(String groupArtifactVersion, CollectRequest request)
      throws VersionRangeResolutionException, ArtifactNotFoundException {

      try {
        logger.info("Resolving dependencies");
        List<Artifact> artifacts = collectDependenciesIntoArtifacts(request);

        logger.info("Building classpath for %s from %d URLs", groupArtifactVersion, artifacts.size());
        List<URL> urls = new ArrayList<URL>();
        for (Artifact artifact : artifacts) {
          urls.add(artifact.getFile().toURI().toURL());
        }

        for (String path : settings.additionalClasspathPaths()) {
          logger.info("Adding \"%s\" to classpath", path);
          urls.add(new File(path).toURI().toURL());
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]), SHARE_NOTHING);
      } catch (Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
          rootCause = rootCause.getCause();
        }
        if (rootCause instanceof VersionRangeResolutionException) {
          throw ((VersionRangeResolutionException) rootCause);
        }
        if (rootCause instanceof ArtifactNotFoundException) {
          throw ((ArtifactNotFoundException) rootCause);
        }
        if (e instanceof SecurityException) {
          throw ((SecurityException) e);
        }
        if (e instanceof DropshipRuntimeException) {
          throw ((DropshipRuntimeException) e);
        }
        if (e instanceof RuntimeException) {
          throw ((RuntimeException) e);
        }
        throw new RuntimeException(e);
      }
    }

    private CollectRequest createCollectRequestForGAV(String gav) {
      DefaultArtifact artifact = new DefaultArtifact(gav);
      Dependency dependency = new Dependency(artifact, COMPILE_SCOPE);

      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRoot(dependency);
      for (RemoteRepository repository : repositories) {
        collectRequest.addRepository(repository);
      }

      return collectRequest;
    }

    private List<Artifact> collectDependenciesIntoArtifacts(CollectRequest collectRequest)
      throws PlexusContainerException, ComponentLookupException, DependencyCollectionException, ArtifactResolutionException, DependencyResolutionException {

      RepositorySystem repositorySystem = newRepositorySystem();
      RepositorySystemSession session = newSession(repositorySystem);
      DependencyNode node = repositorySystem.collectDependencies(session, collectRequest).getRoot();

      DependencyFilter filter = new ScopeDependencyFilter();

      DependencyRequest request = new DependencyRequest(node, filter);

      repositorySystem.resolveDependencies(session, request);

      // PathRecordingDependencyVisitor will give every path, which may help in building a hierarchical class loader
      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);

      return nlg.getArtifacts(false);
    }

    private RepositorySystem newRepositorySystem() throws PlexusContainerException, ComponentLookupException {
      return new DefaultPlexusContainer().lookup(RepositorySystem.class);
    }

    private RepositorySystemSession newSession(RepositorySystem system) {
      MavenRepositorySystemSession session = new MavenRepositorySystemSession();

      session.setOffline(settings.offlineMode());
      session.setRepositoryListener(logger.listener());
      session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
      session.setIgnoreInvalidArtifactDescriptor(false);
      session.setIgnoreMissingArtifactDescriptor(false);
      session.setNotFoundCachingEnabled(false);
      session.setTransferErrorCachingEnabled(false);
      session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);

      LocalRepository localRepo = new LocalRepository(localRepositoryDirectory);
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(localRepo));

      return session;
    }
  }

  /**
   * Creates a classloader that will resolve artifacts against the default "central" repository. Throws
   * {@link IllegalArgumentException} if the GAV is invalid, {@link NullPointerException} if the GAV is null.
   *
   * @param gav artifact group:artifact:version, i.e. joda-time:joda-time:1.6.2
   * @return a classloader that can be used to load classes from the given artifact
   */
  static URLClassLoader createClassLoader(Settings settings, Logger logger, String gav) {
    return usingCentralRepo(settings, logger).createClassLoader(checkNotNull(gav));
  }

  static ArtifactResolutionBuilder using(Settings settings, Logger logger, String url) {
    RemoteRepository custom = new RemoteRepository("custom", "default", url);
    return new ArtifactResolutionBuilder(settings, logger, custom);
  }

  static ArtifactResolutionBuilder usingCentralRepo(Settings settings, Logger logger) {
    RemoteRepository central = new RemoteRepository("central", "default", "http://repo1.maven.org/maven2/");
    return new ArtifactResolutionBuilder(settings, logger, central);
  }

}
