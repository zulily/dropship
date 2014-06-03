package dropship;

public class Preconditions {

  public static <T> T checkNotNull(T obj) {
    if (obj == null) {
      throw new NullPointerException();
    } else {
      return obj;
    }
  }

  public static <T> T checkNotNull(T obj, String msg, Object... args) {
    if (obj == null) {
      throw new NullPointerException(String.format(msg, args));
    } else {
      return obj;
    }
  }

  public static void checkState(boolean state, String msg, Object... args) {
    if (!state) {
      throw new IllegalStateException(String.format(msg, args));
    }
  }

  public static void checkArgument(boolean condition) {
    if (!condition) {
      throw new IllegalStateException();
    }
  }

  public static void checkArgument(boolean condition, String msg, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(msg, args));
    }
  }
}
