package org.immregistries.mismo.trainer.runner;

import org.immregistries.mismo.optimizer.protocol.ErrorCode;

/** Thrown by {@link RunnerApi} handlers; {@code RunnerApiServlet} turns it into an error response. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode getCode() {
    return code;
  }
}
