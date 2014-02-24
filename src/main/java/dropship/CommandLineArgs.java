package dropship;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class CommandLineArgs {

  @Module(library = true, complete = false)
  static class ArgsModule {

    @Provides
    @Singleton
    CommandLineArgs provideArgs(Settings settings, Logger logger, @Named("args") String[] args) {
      checkNotNull(args, "args");
      checkArgument(args.length > 0, "Must specify groupId:artifactId[:version] and classname or valid alias!");

      // If the first argument contains a ':', we will assume that Dropship is being used in
      // the original group:artifact[:version] classname mode.
      if (args[0].contains(":")) {
        return new ExplicitArtifactArguments(settings, logger, args);
      } else {
        return new AliasArguments(settings, logger, args);
      }
    }
  }

  protected Settings settings;
  protected Logger logger;

  protected CommandLineArgs(Settings settings, Logger logger) {
    this.settings = checkNotNull(settings, "settings");
    this.logger = checkNotNull(logger, "logger");
  }

  final String groupArtifactString() {
    String requestedArtifact = requestedArtifact();
    return resolveArtifact(requestedArtifact);
  }

  abstract String requestedArtifact();

  abstract String resolveArtifact(String request);

  abstract String mainClassName();

  abstract ImmutableList<String> commandLineArguments();

  protected String resolveArtifactFromGroupArtifactId(String request) {
    ImmutableList<String> tokens = ImmutableList.copyOf(Settings.GAV_SPLITTER.split(request));

    checkArgument(tokens.size() > 1, "Require groupId:artifactId[:version]");
    checkArgument(tokens.size() < 4, "Require groupId:artifactId[:version]");

    if (tokens.size() == 3) {
      return request;
    }

    String resolvedArtifactId = settings.loadProperty(request).or("[0,)");
    return Settings.GAV_JOINER.join(tokens.get(0), tokens.get(1), resolvedArtifactId);
  }

  private static final class ExplicitArtifactArguments extends CommandLineArgs {

    private final String requestedArtifact;
    private final String mainClassName;
    private final Iterable<String> args;

    public ExplicitArtifactArguments(Settings settings, Logger logger, String[] args) {
      super(settings, logger);
      checkArgument(args.length >= 2);
      this.requestedArtifact = args[0];
      this.mainClassName = args[1];
      this.args = Iterables.skip(Arrays.asList(args), 2);
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
    String mainClassName() {
      return mainClassName;
    }

    @Override
    ImmutableList<String> commandLineArguments() {
      return ImmutableList.copyOf(args);
    }
  }

  private static final class AliasArguments extends CommandLineArgs {

    private final String alias;
    private final Iterable<String> args;

    public AliasArguments(Settings settings, Logger logger, String[] args) {
      super(settings, logger);
      checkArgument(args.length >= 1);
      this.alias = args[0];
      this.args = Iterables.skip(Arrays.asList(args), 1);
    }

    @Override
    String requestedArtifact() {
      return alias;
    }

    @Override
    String resolveArtifact(String request) {

      String aliasPropertyName = "alias." + request;
      Optional<String> resolvedAlias = settings.loadProperty(aliasPropertyName);

      if (resolvedAlias.isPresent()) {
        return resolveArtifactFromGroupArtifactId(Settings.ALIAS_SPLITTER.splitToList(resolvedAlias.get()).get(0));
      } else {
        throw new RuntimeException("Could not resolve alias \"" + request + "\" to artifact ID. Make sure \"alias." + request + "\" is configured in dropship properties.");
      }
    }

    @Override
    String mainClassName() {

      String aliasPropertyName = "alias." + alias;
      Optional<String> resolvedAlias = settings.loadProperty(aliasPropertyName);

      if (resolvedAlias.isPresent()) {
        return Settings.ALIAS_SPLITTER.splitToList(resolvedAlias.get()).get(1);
      } else {
        throw new RuntimeException("Could not resolve alias \"" + alias + "\" to main class name. Make sure \"alias." + alias + "\" is configured in dropship properties.");
      }
    }

    @Override
    ImmutableList<String> commandLineArguments() {
      return ImmutableList.copyOf(args);
    }
  }

}
