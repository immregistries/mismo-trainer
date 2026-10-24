package org.immregistries.mismo.trainer.runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonParseException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.optimizer.protocol.ErrorCode;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.trainer.model.AppSetting;
import org.immregistries.mismo.trainer.model.Runner;
import org.immregistries.mismo.trainer.servlet.HomeServlet;

/**
 * The Runner API, mapped to {@code /api/v1/*} (docs/island-runner-implementation-plan.md D1, §4).
 * Splits the path into segments and dispatches to {@link RunnerApi}. Every route except
 * {@code POST /runner-pairings} requires {@code Authorization: Bearer <credential>};
 * {@code AuthenticationFilter} lets these paths through without a website session.
 *
 * <p>Every request is refused with {@code CLIENT_UNSUPPORTED} if the runner's protocol differs or
 * its version is below {@code app_setting.runner.min.client.version}. Failed pairing attempts are
 * rate-limited per address ({@link PairingRateLimiter}). The runner's contact is committed in its
 * own transaction; the handler's work in a second one, committed only if it succeeds. Errors are
 * always {@code {"error": CODE, "message": text}}.
 */
public class RunnerApiServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger LOGGER = Logger.getLogger(RunnerApiServlet.class.getName());

  private static final String JSON = "application/json";
  private static final String YAML = "text/yaml";

  private static final PairingRateLimiter PAIRING_LIMITER = new PairingRateLimiter();

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getPathInfo() == null ? "" : req.getPathInfo();
    String[] segments = path.replaceAll("^/+|/+$", "").split("/");
    String method = req.getMethod();
    RunnerApi.ClientInfo clientInfo = new RunnerApi.ClientInfo(req.getHeader(Protocol.HEADER_CLIENT_VERSION),
        req.getHeader(Protocol.HEADER_PROTOCOL));

    Session dataSession = HomeServlet.getSessionFactory().openSession();
    Transaction transaction = null;
    try {
      AppSetting minimum = (AppSetting) dataSession.get(AppSetting.class, RunnerApi.SETTING_MIN_CLIENT_VERSION);
      RunnerApi.checkClientVersion(clientInfo, minimum == null ? null : minimum.getSettingValue());
      String body = "GET".equals(method) ? null : readBody(req);
      Response response;
      if (segments.length == 1 && "runner-pairings".equals(segments[0]) && "POST".equals(method)) {
        String address = req.getRemoteAddr();
        if (PAIRING_LIMITER.isBlocked(address)) {
          throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
              "Too many failed pairing attempts from this address. Wait 15 minutes and try again.");
        }
        transaction = dataSession.beginTransaction();
        try {
          response = Response.json(RunnerApi.pair(dataSession, parse(body, Messages.PairingRequest.class)));
        } catch (ApiException e) {
          if (e.getCode() == ErrorCode.UNAUTHORIZED) {
            PAIRING_LIMITER.recordFailure(address);
          }
          throw e;
        }
        transaction.commit();
      } else {
        // Contact is committed on its own, so "last seen" is recorded even if the request then fails.
        transaction = dataSession.beginTransaction();
        Runner runner = RunnerApi.authenticate(dataSession, bearer(req), clientInfo);
        transaction.commit();
        transaction = dataSession.beginTransaction();
        response = route(dataSession, method, segments, body, runner, clientInfo);
        transaction.commit();
      }
      write(resp, 200, response.contentType(), response.body());
    } catch (ApiException e) {
      rollback(transaction);
      writeError(resp, e.getCode(), e.getMessage());
    } catch (JsonParseException e) {
      rollback(transaction);
      writeError(resp, ErrorCode.BAD_REQUEST, "The request body is not valid JSON for this route.");
    } catch (RuntimeException e) {
      rollback(transaction);
      LOGGER.log(Level.SEVERE, "Runner API " + method + " " + path + " failed", e);
      writeError(resp, ErrorCode.SERVER_ERROR, "Mismo Trainer could not complete the request.");
    } finally {
      dataSession.close();
    }
  }

  private static void rollback(Transaction transaction) {
    if (transaction != null && transaction.isActive()) {
      transaction.rollback();
    }
  }

  private record Response(String contentType, String body) {
    static Response json(Object value) {
      return new Response(JSON, Protocol.gson().toJson(value));
    }
  }

  /** Every route except pairing; {@code runner} is already authenticated. */
  private static Response route(Session dataSession, String method, String[] segments, String body, Runner runner,
      RunnerApi.ClientInfo clientInfo) {
    String first = segments[0];
    switch (first) {
    case "runners":
      if (segments.length == 2 && "me".equals(segments[1]) && "GET".equals(method)) {
        return Response.json(RunnerApi.me(runner));
      }
      if (segments.length == 3 && "me".equals(segments[1]) && "heartbeats".equals(segments[2])
          && "POST".equals(method)) {
        // Authentication already recorded the contact.
        return Response.json(RunnerApi.me(runner));
      }
      if (segments.length == 3 && "me".equals(segments[1]) && "assignment".equals(segments[2])
          && "GET".equals(method)) {
        return Response.json(RunnerApi.assignment(dataSession, runner));
      }
      break;
    case "configurations":
      if (segments.length == 3 && "content".equals(segments[2]) && "GET".equals(method)) {
        return new Response(YAML, RunnerApi.configurationContent(dataSession, runner, id(segments[1])));
      }
      break;
    case "test-set-snapshots":
      if (segments.length == 3 && "content".equals(segments[2]) && "GET".equals(method)) {
        return new Response(JSON, RunnerApi.testSetSnapshotContent(dataSession, runner, id(segments[1])));
      }
      break;
    case "runs":
      if (segments.length == 1 && "POST".equals(method)) {
        return Response.json(RunnerApi.createRun(dataSession, runner, parse(body, Messages.CreateRunRequest.class),
            clientInfo));
      }
      if (segments.length == 2 && "GET".equals(method)) {
        return Response.json(RunnerApi.getRun(dataSession, runner, id(segments[1])));
      }
      if (segments.length == 3 && "POST".equals(method)) {
        int runId = id(segments[1]);
        switch (segments[2]) {
        case "progress":
          return Response.json(RunnerApi.progress(dataSession, runner, runId,
              parse(body, Messages.ProgressReport.class)));
        case "candidates":
          return Response.json(RunnerApi.candidate(dataSession, runner, runId,
              parse(body, Messages.CandidateUpload.class)));
        case "stop":
          return Response.json(RunnerApi.stop(dataSession, runner, runId, parse(body, Messages.StopRequest.class)));
        case "fail":
          return Response.json(RunnerApi.fail(dataSession, runner, runId, parse(body, Messages.StopRequest.class)));
        default:
          break;
        }
      }
      break;
    default:
      break;
    }
    throw new ApiException(ErrorCode.NOT_FOUND, "No such API route: " + method + " /" + String.join("/", segments));
  }

  private static <T> T parse(String body, Class<T> type) {
    if (body == null || body.isBlank()) {
      return null;
    }
    return Protocol.gson().fromJson(body, type);
  }

  private static int id(String segment) {
    try {
      return Integer.parseInt(segment);
    } catch (NumberFormatException e) {
      throw new ApiException(ErrorCode.NOT_FOUND, "'" + segment + "' is not an id.");
    }
  }

  private static String bearer(HttpServletRequest req) {
    String header = req.getHeader(Protocol.HEADER_AUTHORIZATION);
    if (header == null || !header.startsWith(Protocol.BEARER_PREFIX)) {
      return null;
    }
    return header.substring(Protocol.BEARER_PREFIX.length());
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    if (req.getContentLengthLong() > Protocol.MAX_REQUEST_BYTES) {
      throw new ApiException(ErrorCode.REQUEST_TOO_LARGE, "The request body is too large.");
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (InputStream in = req.getInputStream()) {
      byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
        if (buffer.size() > Protocol.MAX_REQUEST_BYTES) {
          throw new ApiException(ErrorCode.REQUEST_TOO_LARGE, "The request body is too large.");
        }
      }
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }

  private static void writeError(HttpServletResponse resp, ErrorCode code, String message) throws IOException {
    write(resp, code.httpStatus(), JSON, Protocol.gson().toJson(new Messages.ErrorResponse(code.name(), message)));
  }

  private static void write(HttpServletResponse resp, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    resp.setStatus(status);
    resp.setContentType(contentType + "; charset=UTF-8");
    resp.setContentLength(bytes.length);
    resp.setHeader("Cache-Control", "no-store");
    resp.getOutputStream().write(bytes);
  }
}
