package dropship;

import dagger.Module;
import dagger.Provides;
import dropship.logging.Logger;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Module(library = true, complete = false)
class SettingsModule {

  @Provides
  @Singleton
  Settings provideSettings(Logger logger, @Named("args") String[] args) {
    checkNotNull(args, "args");
    checkArgument(args.length > 0, "Must specify groupId:artifactId[:version] and classname or valid alias!");

    // If the first argument contains a ':', we will assume that Dropship is being used in
    // the original group:artifact[:version] classname mode.
    if (args[0].contains(":")) {
      return new Settings.ExplicitArtifactArguments(logger, args);
    } else {
      return new Settings.AliasArguments(logger, args);
    }
  }
}
