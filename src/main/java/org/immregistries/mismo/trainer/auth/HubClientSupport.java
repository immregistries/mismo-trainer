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
 * Builds and caches the {@link InteropHubClient}, reading both the Hub's
 * base URL and this app's own base URL from the database ({@code
 * app_setting}) rather than build/deploy-time config -- the "Clear"
 * integration pattern documented in InteropHub-Client's
 * docs/integration-clear.md, chosen over StepIntoCDSI's static-final/
 * Maven-filtered-property pattern because this app already has Hibernate/
 * session-factory plumbing that a one-row settings table fits naturally
 * into. The client is rebuilt automatically if either stored URL changes,
 * so updating {@code app_setting.hub.external.url} or {@code
 * app_setting.mismo.external.url} never requires a redeploy or restart.
 */
public final class HubClientSupport {

  public static final String APP_CODE = "mismo";

  private static final String SETTING_HUB_EXTERNAL_URL = "hub.external.url";
  private static final String SETTING_MISMO_EXTERNAL_URL = "mismo.external.url";
  private static final String DEFAULT_MISMO_EXTERNAL_URL = "http://localhost:8080/mismo";
  private static final int CONNECT_TIMEOUT_MS = 8000;
  private static final int READ_TIMEOUT_MS = 12000;

  private static volatile InteropHubClient hubClient;
  private static volatile String lastKnownHubUrl;
  private static volatile String lastKnownMismoUrl;

  private HubClientSupport() {
  }

  /**
   * Returns the Hub client, (re)building it if either URL stored in the
   * database has changed. Returns {@code null} if no Hub URL is configured
   * yet.
   */
  public static InteropHubClient getInteropHubClient() {
    String hubUrl = loadSetting(SETTING_HUB_EXTERNAL_URL, null);
    String mismoUrl = loadSetting(SETTING_MISMO_EXTERNAL_URL, DEFAULT_MISMO_EXTERNAL_URL);
    if (hubUrl == null || hubUrl.isBlank()) {
      return null;
    }
    if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)
        || !mismoUrl.equals(lastKnownMismoUrl)) {
      synchronized (HubClientSupport.class) {
        if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)
            || !mismoUrl.equals(lastKnownMismoUrl)) {
          hubClient = InteropHubClientFactory.create(new HubClientConfig(
              mismoUrl, hubUrl, APP_CODE, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS));
          lastKnownHubUrl = hubUrl;
          lastKnownMismoUrl = mismoUrl;
        }
      }
    }
    return hubClient;
  }

  private static String loadSetting(String key, String fallback) {
    Session session = HomeServlet.getSessionFactory().openSession();
    try {
      AppSetting setting = (AppSetting) session.get(AppSetting.class, key);
      String value = setting == null ? null : setting.getSettingValue();
      return (value == null || value.isBlank()) ? fallback : value;
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
    String basePath = loadSetting(SETTING_MISMO_EXTERNAL_URL, DEFAULT_MISMO_EXTERNAL_URL);
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
