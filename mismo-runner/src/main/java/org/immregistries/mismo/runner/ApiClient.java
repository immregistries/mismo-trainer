package org.immregistries.mismo.runner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonParseException;

import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;

/**
 * Calls the Runner API with the JDK's {@link HttpClient}. Every request carries the client and
 * protocol versions; every request except pairing carries the bearer credential. A non-2xx
 * response or a connection failure becomes an {@link ApiError} with a message fit to print.
 */
final class ApiClient {

  /** Failure of one API call. {@code code} is an {@code ErrorCode} name, or {@code CONNECTION}. */
  static final class ApiError extends Exception {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final int status;

    ApiError(String code, int status, String message) {
      super(message);
      this.code = code;
      this.status = status;
    }

    String code() {
      return code;
    }

    int status() {
      return status;
    }

    boolean isConnectionFailure() {
      return "CONNECTION".equals(code);
    }
  }

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final String serverUrl;
  private final String apiBase;
  private final String credential;

  /** @param credential {@code null} only for pairing */
  ApiClient(String serverUrl, String credential) {
    this.serverUrl = stripTrailingSlash(serverUrl);
    this.apiBase = this.serverUrl + Protocol.API_PREFIX;
    this.credential = credential;
  }

  static String stripTrailingSlash(String url) {
    String trimmed = url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  /**
   * A plain {@code http://} address is refused unless it is this computer, so the credential is
   * never sent unencrypted over a network.
   *
   * @return {@code null} if acceptable, otherwise why not
   */
  static String checkServerUrl(String url) {
    URI uri;
    try {
      uri = URI.create(stripTrailingSlash(url));
    } catch (IllegalArgumentException e) {
      return "That is not a valid address.";
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    if (scheme.equals("https") && !host.isEmpty()) {
      return null;
    }
    if (scheme.equals("http") && (host.equals("localhost") || host.equals("127.0.0.1"))) {
      return null;
    }
    if (scheme.equals("http")) {
      return "Use an https:// address. Plain http:// is only allowed for localhost.";
    }
    return "The address must start with https:// (for example https://informatics.immregistries.org/mismo).";
  }

  String serverUrl() {
    return serverUrl;
  }

  Messages.PairingResponse pair(Messages.PairingRequest request) throws ApiError {
    return json(post("/runner-pairings", request), Messages.PairingResponse.class);
  }

  Messages.RunnerIdentity me() throws ApiError {
    return json(get("/runners/me"), Messages.RunnerIdentity.class);
  }

  Messages.Assignment assignment() throws ApiError {
    return json(get("/runners/me/assignment"), Messages.Assignment.class);
  }

  /** @param path a content {@code url} from the assignment, relative to the API prefix */
  byte[] content(String path) throws ApiError {
    return get(path);
  }

  Messages.RunInfo createRun(Messages.CreateRunRequest request) throws ApiError {
    return json(post("/runs", request), Messages.RunInfo.class);
  }

  Messages.RunInfo progress(int runId, Messages.ProgressReport report) throws ApiError {
    return json(post("/runs/" + runId + "/progress", report), Messages.RunInfo.class);
  }

  Messages.CandidateReceipt candidate(int runId, Messages.CandidateUpload upload) throws ApiError {
    return json(post("/runs/" + runId + "/candidates", upload), Messages.CandidateReceipt.class);
  }

  Messages.RunInfo stop(int runId, String reason) throws ApiError {
    return json(post("/runs/" + runId + "/stop", new Messages.StopRequest(reason)), Messages.RunInfo.class);
  }

  Messages.RunInfo fail(int runId, String reason) throws ApiError {
    return json(post("/runs/" + runId + "/fail", new Messages.StopRequest(reason)), Messages.RunInfo.class);
  }

  Messages.RunInfo run(int runId) throws ApiError {
    return json(get("/runs/" + runId), Messages.RunInfo.class);
  }

  Messages.RunnerIdentity heartbeat() throws ApiError {
    return json(post("/runners/me/heartbeats", null), Messages.RunnerIdentity.class);
  }

  private byte[] get(String path) throws ApiError {
    return send(builder(path).GET().build());
  }

  private byte[] post(String path, Object body) throws ApiError {
    byte[] json = Protocol.gson().toJson(body).getBytes(StandardCharsets.UTF_8);
    return send(builder(path).header("Content-Type", "application/json; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build());
  }

  private HttpRequest.Builder builder(String path) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiBase + path))
        .timeout(Duration.ofSeconds(120))
        .header(Protocol.HEADER_CLIENT_VERSION, RunnerMain.VERSION)
        .header(Protocol.HEADER_PROTOCOL, Protocol.PROTOCOL_VERSION)
        .header("Accept", "application/json, text/yaml");
    if (credential != null) {
      builder.header(Protocol.HEADER_AUTHORIZATION, Protocol.BEARER_PREFIX + credential);
    }
    return builder;
  }

  private byte[] send(HttpRequest request) throws ApiError {
    HttpResponse<byte[]> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      RunnerLog.LOG.warning(request.method() + " " + request.uri().getPath() + " failed: " + e);
      throw new ApiError("CONNECTION", 0, "Could not reach Mismo Trainer at " + serverUrl + " ("
          + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ").");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiError("CONNECTION", 0, "Interrupted while contacting Mismo Trainer.");
    }
    if (response.statusCode() / 100 == 2) {
      return response.body();
    }
    RunnerLog.LOG.warning(request.method() + " " + request.uri().getPath() + " -> HTTP " + response.statusCode()
        + ": " + new String(response.body(), StandardCharsets.UTF_8));
    String text = new String(response.body(), StandardCharsets.UTF_8);
    try {
      Messages.ErrorResponse error = Protocol.gson().fromJson(text, Messages.ErrorResponse.class);
      if (error != null && error.error() != null) {
        throw new ApiError(error.error(), response.statusCode(), error.message());
      }
    } catch (JsonParseException e) {
      // Not one of our error bodies (e.g. a proxy page); fall through.
    }
    throw new ApiError("HTTP_" + response.statusCode(), response.statusCode(),
        "Mismo Trainer answered with HTTP " + response.statusCode() + ". Check the server address.");
  }

  private static <T> T json(byte[] body, Class<T> type) throws ApiError {
    try {
      return Protocol.gson().fromJson(new String(body, StandardCharsets.UTF_8), type);
    } catch (JsonParseException e) {
      throw new ApiError("BAD_RESPONSE", 200, "Mismo Trainer sent a response this runner can't read.");
    }
  }
}
