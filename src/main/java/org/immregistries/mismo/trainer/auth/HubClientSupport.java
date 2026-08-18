package org.immregistries.mismo.trainer.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hibernate.Session;
import org.immregistries.interophub.client.HubClientConfig;
import org.immregistries.interophub.client.InteropHubClient;
import org.immregistries.interophub.client.InteropHubClientFactory;
import org.immregistries.mismo.trainer.model.AppSetting;
import org.immregistries.mismo.trainer.servlet.HomeServlet;

/**
 * Builds and caches the {@link InteropHubClient}, reading the Hub's base URL
 * from the database rather than a build-time property -- the "Clear"
 * integration pattern documented in InteropHub-Client's
 * docs/integration-clear.md, chosen over StepIntoCDSI's static-final/
 * Maven-filtered-property pattern because this app already has Hibernate/
 * session-factory plumbing that a one-row settings table fits naturally
 * into. The client is rebuilt automatically if the stored Hub URL changes,
 * so updating {@code app_setting.hub.external.url} does not require a
 * redeploy.
 */
public final class HubClientSupport {

  public static final String APP_CODE = "mismo";

  /**
   * This app's own externally-reachable base URL. Unlike the Hub URL, this
   * is deploy-time config (matches the Clear precedent, where the app's own
   * URL stays a build-time property) -- it must match whatever context path
   * the WAR is actually deployed at.
   */
  public static final String MISMO_EXTERNAL_URL =
      System.getProperty("mismo.external.url", "http://localhost:8080/mismo");

  private static final String SETTING_HUB_EXTERNAL_URL = "hub.external.url";
  private static final int CONNECT_TIMEOUT_MS = 8000;
  private static final int READ_TIMEOUT_MS = 12000;

  private static volatile InteropHubClient hubClient;
  private static volatile String lastKnownHubUrl;

  private HubClientSupport() {
  }

  /**
   * Returns the Hub client, (re)building it if the Hub URL stored in the
   * database has changed. Returns {@code null} if no Hub URL is configured
   * yet.
   */
  public static InteropHubClient getInteropHubClient() {
    String hubUrl = loadHubExternalUrl();
    if (hubUrl == null || hubUrl.isBlank()) {
      return null;
    }
    if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)) {
      synchronized (HubClientSupport.class) {
        if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)) {
          hubClient = InteropHubClientFactory.create(new HubClientConfig(
              MISMO_EXTERNAL_URL, hubUrl, APP_CODE, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS));
          lastKnownHubUrl = hubUrl;
        }
      }
    }
    return hubClient;
  }

  private static String loadHubExternalUrl() {
    Session session = HomeServlet.getSessionFactory().openSession();
    try {
      AppSetting setting = (AppSetting) session.get(AppSetting.class, SETTING_HUB_EXTERNAL_URL);
      return setting == null ? null : setting.getSettingValue();
    } finally {
      session.close();
    }
  }

  public static void redirectToHubLogin(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    InteropHubClient client = getInteropHubClient();
    if (client == null) {
      response.setContentType("text/plain");
      response.getWriter().println(
          "InteropHub is not configured -- set the 'hub.external.url' row in app_setting.");
      return;
    }
    response.sendRedirect(client.buildLoginUrl(getCurrentUrl(request)));
  }

  public static String getCurrentUrl(HttpServletRequest request) {
    String basePath = MISMO_EXTERNAL_URL;
    if (basePath.endsWith("/")) {
      basePath = basePath.substring(0, basePath.length() - 1);
    }
    String requestPath = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
      requestPath = requestPath.substring(contextPath.length());
    }
    String query = request.getQueryString();
    String result = basePath + requestPath;
    return (query != null && !query.isEmpty()) ? result + "?" + query : result;
  }
}
