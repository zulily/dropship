package dropship;

import static dropship.Preconditions.checkNotNull;

class DropshipRuntimeException extends RuntimeException {

  DropshipRuntimeException(String message) {
    super(checkNotNull(message, "message"));
  }
}
