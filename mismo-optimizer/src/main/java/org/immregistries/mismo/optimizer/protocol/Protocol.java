package org.immregistries.mismo.optimizer.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Constants and the shared Gson instance for the Runner API (docs/island-runner-implementation-plan.md
 * D1, §4). The webapp and the runner both serialize the records in {@link Messages} through
 * {@link #gson()}, so the two sides always agree on the wire format.
 */
public final class Protocol {

  /** Sent by the runner as {@link #HEADER_PROTOCOL}; bumped only for incompatible changes. */
  public static final String PROTOCOL_VERSION = "1";

  public static final String API_PREFIX = "/api/v1";
  public static final String HEADER_CLIENT_VERSION = "X-Mismo-Client-Version";
  public static final String HEADER_PROTOCOL = "X-Mismo-Protocol";
  public static final String HEADER_AUTHORIZATION = "Authorization";
  public static final String BEARER_PREFIX = "Bearer ";

  /** Request bodies above this size are refused; candidates are the largest at roughly 30 KB. */
  public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;

  /** Format of {@link Messages.TestSetContent}; bumped if the case representation changes. */
  public static final int TEST_SET_FORMAT_VERSION = 1;

  public static final String RUN_RUNNING = "RUNNING";
  public static final String RUN_STOPPED = "STOPPED";
  public static final String RUN_FAILED = "FAILED";
  public static final String RUN_ABANDONED = "ABANDONED";

  // HTML escaping is off: patient data is full of '=', which Gson would otherwise write as =.
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private Protocol() {
  }

  public static Gson gson() {
    return GSON;
  }
}
