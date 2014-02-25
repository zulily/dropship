package dropship;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

@Singleton
class Settings {

  private static final CharMatcher GAV_DELIMITER = CharMatcher.is(':');
  private static final CharMatcher ALIAS_DELIMITER = CharMatcher.is('/');
  private static final String DEFAULT_CONFIG_FILE_NAME = "dropship.properties";
  private static final Splitter CSV = Splitter.on(',').trimResults().omitEmptyStrings();

  static final Joiner GAV_JOINER = Joiner.on(':');
  static final Splitter GAV_SPLITTER = Splitter.on(GAV_DELIMITER).trimResults().omitEmptyStrings();
  static final Splitter ALIAS_SPLITTER = Splitter.on(ALIAS_DELIMITER).trimResults().omitEmptyStrings().limit(2);

  private final Logger logger;
  private final Properties cache = new Properties();

  private volatile boolean loaded = false;

  @Inject
  Settings(Logger logger) {
    this.logger = checkNotNull(logger, "logger");
  }

  Optional<String> mavenRepoUrl() {
    return loadProperty("repo.remote.url");
  }

  String localRepoPath() {
    return loadProperty("repo.local.path", ".m2/repository");
  }

  String dropshipVersion() {
    return loadProperty("dropship.xArtifactVersion", "0.0");
  }

  List<String> additionalClasspathPaths() {
    Optional<String> additionalClasspathPathsString = loadProperty("dropship.additional.paths");
    if (additionalClasspathPathsString.isPresent()) {
      return ImmutableList.copyOf(CSV.split(additionalClasspathPathsString.get()));
    } else {
      return ImmutableList.of();
    }
  }

  Optional<String> statsdHost() {
    return loadProperty("statsd.host");
  }

  Optional<Integer> statsdPort() {
    Optional<String> statsdPortString = loadProperty("statsd.port");
    if (statsdPortString.isPresent()) {
      return Optional.of(Integer.parseInt(statsdPortString.get()));
    } else {
      return Optional.absent();
    }
  }

  double statsdSampleRate() {
    Optional<String> statsdSampleRateString = loadProperty("statsd.samplerate");
    if (statsdSampleRateString.isPresent()) {
      return Double.parseDouble(statsdSampleRateString.get());
    } else {
      return 1D;
    }
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
    File propertiesFile = new File(DEFAULT_CONFIG_FILE_NAME);
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
        String versionPropertiesKey = "dropship." + CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, manifestEntryKey);
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
}
