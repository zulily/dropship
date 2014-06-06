package dropship;

public class Preconditions {

  public static <T> T checkNotNull(T obj) {
    if (obj == null) {
      throw new NullPointerException();
    } else {
      return obj;
    }
  }

  public static <T> T checkNotNull(T obj, String msg) {
    if (obj == null) {
      throw new NullPointerException(msg);
    } else {
      return obj;
    }
  }

  public static void checkState(boolean state, String msg) {
    if (!state) {
      throw new IllegalStateException(msg);
    }
  }

  public static void checkArgument(boolean condition) {
    if (!condition) {
      throw new IllegalStateException();
    }
  }

  public static void checkArgument(boolean condition, String msg) {
    if (!condition) {
      throw new IllegalStateException(msg);
    }
  }
}
