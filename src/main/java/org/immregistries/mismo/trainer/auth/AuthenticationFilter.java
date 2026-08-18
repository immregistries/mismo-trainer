package org.immregistries.mismo.trainer.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.servlet.HomeServlet;

/**
 * Requires an InteropHub-authenticated session on every request
 * (database-schema-migration-plan.md §2.8) -- there is no anonymous/guest
 * mode anywhere in v2. The only paths left out are the Hub login callback
 * itself and the Island machine API's POST endpoint on {@code CentralServlet},
 * which authenticates separately via {@code island_credential} in Phase 5.
 */
public class AuthenticationFilter implements Filter {

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    String path = resolvePath(httpRequest);

    if (path.equals("/login") || isIslandApiRequest(httpRequest, path)) {
      chain.doFilter(request, response);
      return;
    }

    User user = (User) httpRequest.getSession(true).getAttribute(HomeServlet.ATTRIBUTE_USER);
    if (user == null) {
      HubClientSupport.redirectToHubLogin(httpRequest, httpResponse);
      return;
    }

    // Protected pages should not be replayable from browser cache after logout.
    httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    httpResponse.setHeader("Pragma", "no-cache");
    httpResponse.setDateHeader("Expires", 0);

    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
  }

  private boolean isIslandApiRequest(HttpServletRequest request, String path) {
    return path.equals("/CentralServlet") && "POST".equalsIgnoreCase(request.getMethod());
  }

  private String resolvePath(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    String uri = request.getRequestURI();
    String path = uri;
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      path = uri.substring(contextPath.length());
    }
    return (path == null || path.isEmpty()) ? "/" : path;
  }
}
