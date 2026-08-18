package org.immregistries.mismo.trainer.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.interophub.client.HubExchangeResult;
import org.immregistries.interophub.client.HubRedirectDecision;
import org.immregistries.interophub.client.HubUserInfo;
import org.immregistries.interophub.client.InteropHubClient;
import org.immregistries.mismo.trainer.auth.HubClientSupport;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.User;

/**
 * Handles InteropHub's redirect back to this app after login
 * (database-schema-migration-plan.md §6 Phase 4). Mapped to "/login" to
 * match the return URL the client library builds automatically
 * ({@code mismoExternalUrl + "/login"}).
 */
public class LoginServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  /**
   * Public/shared email providers that must not be treated as a reusable
   * organization domain (database-schema-migration-plan.md §2.4).
   */
  private static final Set<String> PUBLIC_EMAIL_DOMAINS = Set.of(
      "gmail.com", "outlook.com", "yahoo.com", "hotmail.com", "icloud.com",
      "aol.com", "live.com", "msn.com", "protonmail.com");

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    InteropHubClient client = HubClientSupport.getInteropHubClient();
    if (client == null) {
      showMessage(resp, "InteropHub is not configured for this deployment.");
      return;
    }

    String code = req.getParameter("code");
    if (code == null || code.trim().isEmpty()) {
      showMessage(resp, "Login was cancelled or no authorization code was received.");
      return;
    }

    HubExchangeResult exchangeResult = client.exchangeCode(code.trim(), req.getRemoteAddr());
    HubUserInfo hubUser = exchangeResult.getUserInfo();

    if (!exchangeResult.isSuccess() || !exchangeResult.hasRequiredUserInfo()) {
      showMessage(resp, "InteropHub login failed: " + exchangeResult.getErrorMessage());
      return;
    }
    // The client library does not itself require hub_user_id to be present
    // (database-schema-migration-plan.md §2.1) -- Mismo-Trainer must.
    if (hubUser.getHubUserId() == null || hubUser.getHubUserId().trim().isEmpty()) {
      showMessage(resp, "InteropHub did not return a hub_user_id; unable to log in.");
      return;
    }

    User user = findOrCreateUser(hubUser);
    req.getSession(true).setAttribute(HomeServlet.ATTRIBUTE_USER, user);

    HubRedirectDecision redirectDecision = client.determineRedirectDecision(
        exchangeResult.getRequestedUrlFromHub(), req.getContextPath() + "/HomeServlet");
    resp.sendRedirect(redirectDecision.getTarget());
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  /**
   * Finds the local user by hub_user_id, creating it (and assigning an
   * organization) on first login; refreshes InteropHub-owned profile fields
   * either way (database-schema-migration-plan.md §2.2, §2.5).
   */
  private User findOrCreateUser(HubUserInfo hubUser) {
    Session dataSession = HomeServlet.getSessionFactory().openSession();
    try {
      Transaction transaction = dataSession.beginTransaction();

      Query query = dataSession.createQuery("from User where hubUserId = ?");
      query.setParameter(0, hubUser.getHubUserId());
      List<User> existing = query.list();

      Date now = new Date();
      User user;
      if (existing.isEmpty()) {
        user = new User();
        user.setHubUserId(hubUser.getHubUserId());
        user.setCreatedAt(now);
        user.setOrganization(resolveOrganization(dataSession, hubUser));
        dataSession.save(user);
      } else {
        user = existing.get(0);
      }

      user.setEmail(hubUser.getEmail());
      user.setDisplayName(hubUser.getName());
      user.setFirstName(hubUser.getFirstName());
      user.setLastName(hubUser.getLastName());
      user.setTitle(hubUser.getTitle());
      user.setUpdatedAt(now);
      user.setLastLoginAt(now);

      transaction.commit();
      return user;
    } finally {
      dataSession.close();
    }
  }

  /**
   * Email-domain organization-assignment heuristic
   * (database-schema-migration-plan.md §2.4) -- used for first-login
   * assignment only; never recomputed on later logins.
   */
  private Organization resolveOrganization(Session dataSession, HubUserInfo hubUser) {
    String domain = extractDomain(hubUser.getEmail());
    boolean reusableDomain = domain != null && !PUBLIC_EMAIL_DOMAINS.contains(domain);

    if (reusableDomain) {
      Query query = dataSession.createQuery("from Organization where domain = ?");
      query.setParameter(0, domain);
      List<Organization> existing = query.list();
      if (!existing.isEmpty()) {
        return existing.get(0);
      }
    }

    Date now = new Date();
    Organization organization = new Organization();
    organization.setName(hubUser.getOrganization());
    organization.setDomain(reusableDomain ? domain : null);
    organization.setCreatedAt(now);
    organization.setUpdatedAt(now);
    dataSession.save(organization);
    return organization;
  }

  private String extractDomain(String email) {
    if (email == null) {
      return null;
    }
    int at = email.lastIndexOf('@');
    if (at < 0 || at == email.length() - 1) {
      return null;
    }
    return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
  }

  private void showMessage(HttpServletResponse resp, String message) throws IOException {
    resp.setContentType("text/html; charset=UTF-8");
    PrintWriter out = resp.getWriter();
    out.println("<!DOCTYPE html><html><head><title>Mismo Match Login</title></head><body>");
    out.println("<h1>Mismo Match Login</h1>");
    out.println("<p>" + escapeHtml(message) + "</p>");
    out.println("</body></html>");
    out.close();
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
