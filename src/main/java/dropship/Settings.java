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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.jar.Manifest;

import static dropship.Preconditions.checkArgument;
import static dropship.Preconditions.checkNotNull;

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

  private static final String DEFAULT_CONFIG_FILE_NAME = "dropship.properties";

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

  abstract List<String> commandLineArguments();

  protected String resolveArtifactFromGroupArtifactId(String request) {
    List<String> tokens = new ArrayList<String>();
    Scanner tokenizer = new Scanner(request).useDelimiter(":");
    while (tokenizer.hasNext()) {
      tokens.add(tokenizer.next());
    }

    checkArgument(tokens.size() > 1, "Require groupId:artifactId[:version]");
    checkArgument(tokens.size() < 4, "Require groupId:artifactId[:version]");

    if (tokens.size() == 3) {
      return request;
    }


    String resolvedArtifactId = loadProperty(request, "[0,)");
    return tokens.get(0) + ":" + tokens.get(1) + ":" + resolvedArtifactId;
  }

  String mavenRepoUrl() {
    return loadProperty("repo.remote-url");
  }

  String localRepoPath() {
    return loadProperty("repo.local-path", ".m2/repository");
  }

  String dropshipVersion() {
    return loadProperty("dropship.x-artifact-version", "0.0");
  }

  List<String> additionalClasspathPaths() {
    String additionalClasspathPathsString = loadProperty("dropship.additional-paths");
    if (additionalClasspathPathsString != null) {
      List<String> paths = new ArrayList<String>();
      Scanner tokenizer = new Scanner(additionalClasspathPathsString).useDelimiter(",");
      while (tokenizer.hasNext()) {
        paths.add(tokenizer.next());
      }
      return paths;
    } else {
      return new LinkedList<String>();
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

  public final Properties asProperties() {
    Properties properties = new Properties(System.getProperties());
    properties.putAll(loadBootstrapPropertiesUnchecked());
    return properties;
  }

  String loadProperty(String name, String defaultValue) {
    checkNotNull(defaultValue);

    String value = loadProperty(name);
    return value != null ? value : defaultValue;
  }

  String loadProperty(String name) {
    String value = loadBootstrapPropertiesUnchecked().getProperty(name);
    if (value == null) {
      value = System.getProperty(name);
    }
    return value;
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
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Properties loadPackageInformation() {
    Properties versionProperties = new Properties();
    Manifest manifest = loadManifest();
    if (manifest != null) {
      for (Map.Entry<Object, Object> attributeEntry : manifest.getMainAttributes().entrySet()) {
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

  private static Manifest loadManifest() {
    String resourceName = Dropship.class.getName().replace('.', '/') + ".class";
    URL location = Dropship.class.getResource(resourceName);
    if (location == null) {
      return null;
    }

    try {
      String classPath = location.toString();
      if (!classPath.startsWith("jar")) {
        // Class not from JAR
        return null;
      }
      String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
      return new Manifest(new URL(manifestPath).openStream());
    } catch (MalformedURLException e) {
      return null;
    } catch (IOException e) {
      return null;
    }
  }

  static final class DownloadModeArguments extends Settings {

    private final String localDownloadDir;
    private final Settings delegate;

    // TODO : scope
    public DownloadModeArguments(Logger logger, Settings delegate, List<String> options) {
      super(logger, delegate.offlineMode());

      // parse --download=/some/local/path
      String path = null;
      for (String option : options) {
        if (option.startsWith("--download=")) {
          path = option.substring("--download=".length());
        }
      }
      checkArgument(
        path != null && !path.isEmpty(),
        "Must specify a local download directory"
      );
      this.localDownloadDir = path;
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
    List<String> commandLineArguments() {
      return delegate.commandLineArguments();
    }

    @Override
    public boolean downloadMode() {
      return this.localDownloadDir != null && !this.localDownloadDir.isEmpty();
    }

    @Override
    public String localDownloadPath() {
      return this.localDownloadDir;
    }
  }

  static final class ExplicitArtifactArguments extends Settings {

    private final String requestedArtifact;
    private final String mainClassName;
    private final List<String> args;

    // TODO : scope
    public ExplicitArtifactArguments(Logger logger, List<String> args, boolean offline, boolean download) {
      super(logger, offline);
      checkArgument(!args.isEmpty(), "Must specify groupId:artifactId[:version]");
      this.requestedArtifact = args.get(0);
      if (download) {
        this.mainClassName = "";
        this.args = new LinkedList<String>();
      } else {
        checkArgument(args.size() >= 2, "Must specify groupId:artifactId[:version] and a main class name!");
        this.mainClassName = args.get(1);
        this.args = args.subList(2, args.size());
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
    List<String> commandLineArguments() {
      return new ArrayList<String>(args);
    }
  }

  static final class AliasArguments extends Settings {

    private final String alias;
    private final List<String> args;

    // TODO : scope
    public AliasArguments(Logger logger, List<String> args, boolean offline) {
      super(logger, offline);
      checkArgument(args.size() >= 1);
      this.alias = args.get(0);
      this.args = args.subList(1, args.size());
    }

    @Override
    String requestedArtifact() {
      return alias;
    }

    @Override
    String resolveArtifact(String request) {

      String aliasPropertyName = "alias." + request;
      String resolvedAlias = loadProperty(aliasPropertyName);

      if (resolvedAlias != null && resolvedAlias.contains("/")) {
        // Split alias on '/', first token is the group and artifact id
        String groupAndArtifactId = resolvedAlias.substring(0, resolvedAlias.indexOf('/'));
        return resolveArtifactFromGroupArtifactId(groupAndArtifactId);
      } else {
        throw new RuntimeException("Could not resolve alias \"" + request + "\" to artifact ID. Make sure \"alias." + request + "\" is configured in dropship properties.");
      }
    }

    @Override
    public String mainClassName() {

      String aliasPropertyName = "alias." + alias;
      String resolvedAlias = loadProperty(aliasPropertyName);

      if (resolvedAlias != null && resolvedAlias.contains("/")) {
        // Everything after first '/' is the class name
        return resolvedAlias.substring(resolvedAlias.indexOf('/') + 1);
      } else {
        throw new RuntimeException("Could not resolve alias \"" + alias + "\" to main class name. Make sure \"alias." + alias + "\" is configured in dropship.properties.");
      }
    }

    @Override
    List<String> commandLineArguments() {
      return new ArrayList<String>(args);
    }
  }

}
