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

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import dropship.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

/**
 * Exposes settings configured via command-line, environment,
 * and/or properties file. Subclasses handle various methods
 * of specifying an artifact to load.
 * <ul>
 *   <li>{@link dropship.Settings.ExplicitArtifactArguments} -
 *   handles explicit {@code group:artifact[:version]} specs</li>
 *   <li>{@link dropship.Settings.AliasArguments} - handles
 *   use of aliases, which map a single token to a group id,
 *   artifact id, and main class</li>
 * </ul>
 * Subclasses exist to handle explicit
 * artifact specification, and aliased artifact specification.
 */
public abstract class Settings {

  private static final CharMatcher GAV_DELIMITER = CharMatcher.is(':');
  private static final CharMatcher ALIAS_DELIMITER = CharMatcher.is('/');
  private static final CharMatcher OPTION_DELIMITER = CharMatcher.is('=');
  private static final String DEFAULT_CONFIG_FILE_NAME = "dropship.properties";
  private static final Splitter CSV = Splitter.on(',').trimResults().omitEmptyStrings();
  private static final Joiner GAV_JOINER = Joiner.on(':');
  private static final Splitter GAV_SPLITTER = Splitter.on(GAV_DELIMITER).trimResults().omitEmptyStrings();
  private static final Splitter ALIAS_SPLITTER = Splitter.on(ALIAS_DELIMITER).trimResults().omitEmptyStrings().limit(2);
  private static final Splitter OPTION_SPLITTER = Splitter.on(OPTION_DELIMITER).trimResults().omitEmptyStrings().limit(2);

  protected final Logger logger;
  private final boolean offlineMode;
  private final Properties cache = new Properties();
  private volatile boolean loaded = false;

  protected Settings(Logger logger, boolean offlineMode) {
    this.logger = checkNotNull(logger, "logger");
    this.offlineMode = offlineMode;
  }

  /**
   * Returns the group and artifact ID specified by the dropship command-line
   * configuration, resolving any aliases if necessary.
   */
  public final String groupArtifactString() {
    String requestedArtifact = requestedArtifact();
    return resolveArtifact(requestedArtifact);
  }

  abstract String requestedArtifact();

  abstract String resolveArtifact(String request);

  /**
   * Returns the name of the "main" class specified by the dropship
   * command-line configuration.
   */
  public abstract String mainClassName();

  abstract ImmutableList<String> commandLineArguments();

  protected String resolveArtifactFromGroupArtifactId(String request) {
    ImmutableList<String> tokens = ImmutableList.copyOf(GAV_SPLITTER.split(request));

    checkArgument(tokens.size() > 1, "Require groupId:artifactId[:version]");
    checkArgument(tokens.size() < 4, "Require groupId:artifactId[:version]");

    if (tokens.size() == 3) {
      return request;
    }

    String resolvedArtifactId = loadProperty(request).or("[0,)");
    return GAV_JOINER.join(tokens.get(0), tokens.get(1), resolvedArtifactId);
  }

  Optional<String> mavenRepoUrl() {
    return loadProperty("repo.remote-url");
  }

  String localRepoPath() {
    return loadProperty("repo.local-path", ".m2/repository");
  }

  String dropshipVersion() {
    return loadProperty("dropship.x-artifact-version", "0.0");
  }

  List<String> additionalClasspathPaths() {
    Optional<String> additionalClasspathPathsString = loadProperty("dropship.additional-paths");
    if (additionalClasspathPathsString.isPresent()) {
      return ImmutableList.copyOf(CSV.split(additionalClasspathPathsString.get()));
    } else {
      return ImmutableList.of();
    }
  }

  /** Returns true if dropship should run in offline mode. */
  public boolean offlineMode() {
    return this.offlineMode || "true".equalsIgnoreCase(loadProperty("dropship.offline", "false"));
  }

  /** Returns true if dropship should run in download mode. */
  public boolean downloadMode() {
    return false;
  }

  /** Returns the location that dropship should download artifacts to when running in download mode. */
  public String localDownloadPath() {
    return "";
  }

  /** Returns the optional hostname of the statsd server to use for basic metrics. */
  public Optional<String> statsdHost() {
    return loadProperty("statsd.host");
  }

  /** Returns the optional port number of the statsd server to use for basic metrics. */
  public Optional<Integer> statsdPort() {
    Optional<String> statsdPortString = loadProperty("statsd.port");
    if (statsdPortString.isPresent()) {
      return Optional.of(Integer.parseInt(statsdPortString.get()));
    } else {
      return Optional.absent();
    }
  }

  /** Returns the sample rate to use when sending metrics to statsd. */
  public double statsdSampleRate() {
    Optional<String> statsdSampleRateString = loadProperty("statsd.sample-rate");
    if (statsdSampleRateString.isPresent()) {
      return Double.parseDouble(statsdSampleRateString.get());
    } else {
      return 1D;
    }
  }

  public final Properties asProperties() {
    Properties properties = new Properties(System.getProperties());
    properties.putAll(loadBootstrapPropertiesUnchecked());
    return properties;
  }

  String loadProperty(String name, String defaultValue) {
    checkNotNull(defaultValue);

    return loadProperty(name).or(defaultValue);
  }

  Optional<String> loadProperty(String name) {
    return Optional.fromNullable(loadBootstrapPropertiesUnchecked().getProperty(name))
      .or(Optional.fromNullable(System.getProperty(name)));
  }

  private synchronized Properties loadBootstrapProperties() throws IOException {
    if (loaded) {
      return cache;
    }

    URL url = null;

    // Try current working directory
    File propertiesFile = new File(".", DEFAULT_CONFIG_FILE_NAME);
    if (propertiesFile.exists()) {
      url = propertiesFile.toURI().toURL();
    }

    if (url == null) {
      url = Dropship.class.getClassLoader().getResource(DEFAULT_CONFIG_FILE_NAME);
    }

    if (url == null) {

      String propertiesLocation = System.getProperty(DEFAULT_CONFIG_FILE_NAME);
      if (propertiesLocation != null) {
        propertiesFile = new File(propertiesLocation);
        if (propertiesFile.exists()) {
          url = propertiesFile.toURI().toURL();
        } else {
          logger.warn("%s at %s does not exist", DEFAULT_CONFIG_FILE_NAME, propertiesLocation);
        }
      }
    }

    if (url == null) {
      logger.warn("No dropship.properties found! Using defaults");
    } else {
      logger.info("Loading configuration from %s", url);
      cache.load(url.openStream());
      for (Map.Entry<Object, Object> entry : cache.entrySet()) {
        logger.debug(" %s: %s = %s", url.getPath(), entry.getKey(), entry.getValue());
      }
    }

    for (Map.Entry<Object, Object> entry : loadPackageInformation().entrySet()) {
      //noinspection UseOfPropertiesAsHashtable
      cache.put(entry.getKey(), entry.getValue());
      logger.debug(" MANIFEST: %s = %s", entry.getKey(), entry.getValue());
    }
    loaded = true;
    return cache;
  }

  private Properties loadBootstrapPropertiesUnchecked() {
    try {
      return loadBootstrapProperties();
    } catch (Exception e) {
      throw propagate(e);
    }
  }

  private static Properties loadPackageInformation() {
    Properties versionProperties = new Properties();
    Optional<Manifest> manifest = loadManifest();
    if (manifest.isPresent()) {
      for (Map.Entry<Object, Object> attributeEntry : manifest.get().getMainAttributes().entrySet()) {
        String manifestEntryKey = attributeEntry.getKey().toString().toLowerCase();
        if (manifestEntryKey.startsWith("X-")) {
          manifestEntryKey = manifestEntryKey.substring(2);
        }
        String versionPropertiesKey = "dropship." + manifestEntryKey.toLowerCase();
        System.setProperty(versionPropertiesKey, attributeEntry.getValue().toString());
        versionProperties.setProperty(versionPropertiesKey, attributeEntry.getValue().toString());
      }
    }
    return versionProperties;
  }

  private static Optional<Manifest> loadManifest() {
    Optional<URL> location = Optional.fromNullable(Resources.getResource(CharMatcher.is('.').replaceFrom(Dropship.class.getName(), '/') + ".class"));
    if (!location.isPresent()) {
      return Optional.absent();
    }

    try {
      String classPath = location.get().toString();
      if (!classPath.startsWith("jar")) {
        // Class not from JAR
        return Optional.absent();
      }
      String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
      return Optional.of(new Manifest(new URL(manifestPath).openStream()));
    } catch (MalformedURLException e) {
      return Optional.absent();
    } catch (IOException e) {
      return Optional.absent();
    }
  }

  static final class DownloadModeArguments extends Settings {

    final static class DownloadOptionPresent implements Predicate<String> {
      @Override
      public boolean apply(String input) {
        return Strings.nullToEmpty(input).startsWith("--download=");
      }
    }

    private final String localDownloadDir;
    private final Settings delegate;

    // TODO : scope
    public DownloadModeArguments(Logger logger, Settings delegate, ImmutableList<String> options) {
      super(logger, delegate.offlineMode());

      // parse --download=/some/local/path
      List<String> downloadOption = OPTION_SPLITTER.splitToList(Iterables.tryFind(options, new DownloadOptionPresent()).or(""));
      checkArgument(
        downloadOption.size() == 2 && !Strings.isNullOrEmpty(downloadOption.get(1)),
        "Must specify a local download directory"
      );
      this.localDownloadDir = downloadOption.get(1);
      this.delegate = checkNotNull(delegate, "delegate");
    }

    @Override
    String requestedArtifact() {
      return delegate.requestedArtifact();
    }

    @Override
    String resolveArtifact(String request) {
      return delegate.resolveArtifact(request);
    }

    @Override
    public String mainClassName() {
      return delegate.mainClassName();
    }

    @Override
    ImmutableList<String> commandLineArguments() {
      return delegate.commandLineArguments();
    }

    @Override
    public boolean downloadMode() {
      return !Strings.isNullOrEmpty(this.localDownloadDir);
    }

    @Override
    public String localDownloadPath() {
      return this.localDownloadDir;
    }
  }

  static final class ExplicitArtifactArguments extends Settings {

    private final String requestedArtifact;
    private final String mainClassName;
    private final Iterable<String> args;

    // TODO : scope
    public ExplicitArtifactArguments(Logger logger, ImmutableList<String> args, boolean offline, boolean download) {
      super(logger, offline);
      checkArgument(!args.isEmpty(), "Must specify groupId:artifactId[:version]");
      this.requestedArtifact = args.get(0);
      if (download) {
        this.mainClassName = "";
        this.args = ImmutableList.of();
      } else {
        checkArgument(args.size() >= 2, "Must specify groupId:artifactId[:version] and a main class name!");
        this.mainClassName = args.get(1);
        this.args = Iterables.skip(args, 2);
      }
    }

    @Override
    String requestedArtifact() {
      return requestedArtifact;
    }

    @Override
    String resolveArtifact(String request) {
      return resolveArtifactFromGroupArtifactId(request);
    }

    @Override
    public String mainClassName() {
      return mainClassName;
    }

    @Override
    ImmutableList<String> commandLineArguments() {
      return ImmutableList.copyOf(args);
    }
  }

  static final class AliasArguments extends Settings {

    private final String alias;
    private final Iterable<String> args;

    // TODO : scope
    public AliasArguments(Logger logger, ImmutableList<String> args, boolean offline) {
      super(logger, offline);
      checkArgument(args.size() >= 1);
      this.alias = args.get(0);
      this.args = Iterables.skip(args, 1);
    }

    @Override
    String requestedArtifact() {
      return alias;
    }

    @Override
    String resolveArtifact(String request) {

      String aliasPropertyName = "alias." + request;
      Optional<String> resolvedAlias = loadProperty(aliasPropertyName);

      if (resolvedAlias.isPresent()) {
        return resolveArtifactFromGroupArtifactId(ALIAS_SPLITTER.splitToList(resolvedAlias.get()).get(0));
      } else {
        throw new RuntimeException("Could not resolve alias \"" + request + "\" to artifact ID. Make sure \"alias." + request + "\" is configured in dropship properties.");
      }
    }

    @Override
    public String mainClassName() {

      String aliasPropertyName = "alias." + alias;
      Optional<String> resolvedAlias = loadProperty(aliasPropertyName);

      if (resolvedAlias.isPresent()) {
        return ALIAS_SPLITTER.splitToList(resolvedAlias.get()).get(1);
      } else {
        throw new RuntimeException("Could not resolve alias \"" + alias + "\" to main class name. Make sure \"alias." + alias + "\" is configured in dropship.properties.");
      }
    }

    @Override
    ImmutableList<String> commandLineArguments() {
      return ImmutableList.copyOf(args);
    }
  }

}
