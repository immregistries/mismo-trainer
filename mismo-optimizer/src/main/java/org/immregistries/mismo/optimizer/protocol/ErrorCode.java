package org.immregistries.mismo.optimizer.protocol;

/**
 * The {@code error} field of every non-2xx Runner API response, so the runner can print a clear
 * message without parsing prose.
 */
public enum ErrorCode {
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  NOT_FOUND(404),
  NO_ASSIGNMENT(404),
  ASSIGNMENT_INVALID(409),
  ASSIGNMENT_CHANGED(409),
  HASH_MISMATCH(409),
  RUN_NOT_ACTIVE(409),
  INVALID_CANDIDATE(422),
  REQUEST_TOO_LARGE(413),
  CLIENT_UNSUPPORTED(426),
  TOO_MANY_ATTEMPTS(429),
  SERVER_ERROR(500);

  private final int httpStatus;

  ErrorCode(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
