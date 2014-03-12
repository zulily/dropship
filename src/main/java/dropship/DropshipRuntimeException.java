package dropship;

import static com.google.common.base.Preconditions.checkNotNull;

class DropshipRuntimeException extends RuntimeException {

  DropshipRuntimeException(String message) {
    super(checkNotNull(message, "message"));
  }
}
