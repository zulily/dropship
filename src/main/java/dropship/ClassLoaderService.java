package dropship;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Singleton
public final class ClassLoaderService {

  private final Settings settings;
  private final MavenClassLoader.ClassLoaderBuilder clBuilder;

  private URLClassLoader classLoader = null;

  @Inject
  ClassLoaderService(Settings settings, MavenClassLoader.ClassLoaderBuilder clBuilder) {
    this.settings = checkNotNull(settings, "settings");
    this.clBuilder = checkNotNull(clBuilder, "class loader builder");
  }

  public synchronized URLClassLoader getClassLoader() {
    if (classLoader == null) {
      classLoader = clBuilder.forMavenCoordinates(settings.groupArtifactString());
    }

    checkState(classLoader != null, "Classloader has not been created");
    return classLoader;
  }
}
