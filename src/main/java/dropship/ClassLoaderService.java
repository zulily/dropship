package dropship;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLClassLoader;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Singleton
final class ClassLoaderService {

  private final CommandLineArgs args;
  private final MavenClassLoader.ClassLoaderBuilder clBuilder;

  private URLClassLoader classLoader = null;

  @Inject
  ClassLoaderService(CommandLineArgs args, MavenClassLoader.ClassLoaderBuilder clBuilder) {
    this.args = checkNotNull(args, "args");
    this.clBuilder = checkNotNull(clBuilder, "class loader builder");
  }

  public synchronized URLClassLoader getClassLoader() {
    if (classLoader == null) {
      classLoader = clBuilder.forMavenCoordinates(args.groupArtifactString());
    }

    checkState(classLoader != null, "Classloader has not been created");
    return classLoader;
  }
}
