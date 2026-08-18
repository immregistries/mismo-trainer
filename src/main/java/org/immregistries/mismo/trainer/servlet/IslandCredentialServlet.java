package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.IslandCredential;
import org.immregistries.mismo.trainer.model.User;

/**
 * Lets a logged-in user create, list, and revoke Island machine credentials for their own
 * organization (database-schema-migration-plan.md §2.6, §3.6). The raw token is only ever
 * available in the response immediately following {@link #ACTION_CREATE} -- it is never stored
 * (only its hash is, via {@link IslandCredentialSupport}) and never shown again after this one
 * render, including on page refresh.
 */
public class IslandCredentialServlet extends HomeServlet {

  public static final String ACTION_CREATE = "Create";
  public static final String ACTION_REVOKE = "Revoke";

  public static final String PARAM_NAME = "name";
  public static final String PARAM_ISLAND_CREDENTIAL_ID = "islandCredentialId";

  private static final String ATTRIBUTE_NEW_TOKEN = "islandCredentialNewToken";
  private static final String ATTRIBUTE_NEW_TOKEN_NAME = "islandCredentialNewTokenName";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(ATTRIBUTE_USER);
    Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
    try {
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }

      String action = req.getParameter(PARAM_ACTION);
      if (ACTION_CREATE.equals(action)) {
        String name = req.getParameter(PARAM_NAME);
        if (name != null && !name.isBlank()) {
          String rawToken = IslandCredentialSupport.generateToken();
          Transaction transaction = dataSession.beginTransaction();
          OrgScope.createIslandCredential(dataSession, name.trim(), IslandCredentialSupport.hash(rawToken), user);
          transaction.commit();
          session.setAttribute(ATTRIBUTE_NEW_TOKEN, rawToken);
          session.setAttribute(ATTRIBUTE_NEW_TOKEN_NAME, name.trim());
        }
      } else if (ACTION_REVOKE.equals(action) && req.getParameter(PARAM_ISLAND_CREDENTIAL_ID) != null) {
        Transaction transaction = dataSession.beginTransaction();
        OrgScope.revokeIslandCredential(dataSession, Integer.parseInt(req.getParameter(PARAM_ISLAND_CREDENTIAL_ID)),
            user);
        transaction.commit();
      }

      // The raw token is a one-time reveal: read it and immediately clear it from the
      // session so a page refresh (or navigating away and back) never shows it again.
      String newToken = (String) session.getAttribute(ATTRIBUTE_NEW_TOKEN);
      String newTokenName = (String) session.getAttribute(ATTRIBUTE_NEW_TOKEN_NAME);
      session.removeAttribute(ATTRIBUTE_NEW_TOKEN);
      session.removeAttribute(ATTRIBUTE_NEW_TOKEN_NAME);

      HomeServlet.doHeader(out, req, user, null);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("    <h1 class=\"aira-page-title\">Island Credentials</h1>");
      out.println(
          "    <p>Island optimization processes authenticate to <a href=\"CentralServlet\">Central</a> with one"
              + " of these credentials instead of an InteropHub login. Each credential is scoped to your"
              + " organization (" + escapeHtml(user.getOrganization() == null ? "" : user.getOrganization().getName())
              + ").</p>");

      if (newToken != null) {
        out.println("      <div class=\"aira-alert aira-alert--warning\" role=\"alert\">");
        out.println("        <p class=\"aira-alert__title\">Copy this token now -- it will not be shown again</p>");
        out.println(
            "        <p>Credential <strong>" + escapeHtml(newTokenName) + "</strong> was created. Paste this value"
                + " into the Island's <code>island.yml</code> as <code>credential</code>:</p>");
        out.println("        <p><code style=\"user-select:all\">" + escapeHtml(newToken) + "</code></p>");
        out.println("      </div>");
      }

      List<IslandCredential> credentialList = OrgScope.listIslandCredentials(dataSession, user);
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr>");
      out.println("        <th>Name</th>");
      out.println("        <th>Created</th>");
      out.println("        <th>Created By</th>");
      out.println("        <th>Last Used</th>");
      out.println("        <th>Status</th>");
      out.println("        <th>Action</th>");
      out.println("      </tr>");
      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
      for (IslandCredential credential : credentialList) {
        boolean revoked = credential.getRevokedAt() != null;
        out.println("      <tr>");
        out.println("        <td>" + escapeHtml(credential.getName()) + "</td>");
        out.println("        <td>" + (credential.getCreatedAt() == null ? "" : sdf.format(credential.getCreatedAt()))
            + "</td>");
        out.println("        <td>"
            + (credential.getCreatedByUser() == null ? "" : escapeHtml(credential.getCreatedByUser().getDisplayName()))
            + "</td>");
        out.println(
            "        <td>" + (credential.getLastUsedAt() == null ? "never" : sdf.format(credential.getLastUsedAt()))
                + "</td>");
        out.println("        <td class=\"" + (revoked ? "fail" : "pass") + "\">" + (revoked ? "Revoked" : "Active")
            + "</td>");
        out.println("        <td>");
        if (!revoked) {
          out.println("          <form action=\"IslandCredentialServlet\" method=\"POST\">");
          out.println("            <input type=\"hidden\" name=\"" + PARAM_ISLAND_CREDENTIAL_ID + "\" value=\""
              + credential.getIslandCredentialId() + "\"/>");
          out.println(
              "            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_REVOKE + "\"/>");
          out.println("          </form>");
        } else {
          out.println("&nbsp;");
        }
        out.println("        </td>");
        out.println("      </tr>");
      }
      out.println("    </table>");

      out.println("    <h3>Create New Credential</h3>");
      out.println("    <form action=\"IslandCredentialServlet\" method=\"POST\">");
      out.println("    <table>");
      out.println("      <tr>");
      out.println("        <td>Name</td>");
      out.println("        <td><input type=\"text\" size=\"30\" name=\"" + PARAM_NAME
          + "\" placeholder=\"e.g. Jacob Lake island\" value=\"\"/></td>");
      out.println("      </tr>");
      out.println("      <tr>");
      out.println("        <td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
          + "\" value=\"" + ACTION_CREATE + "\"/></td>");
      out.println("      </tr>");
      out.println("    </table>");
      out.println("    </form>");
      out.println("    </div>");

      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    } finally {
      out.close();
      teardown(req, resp);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doGet(req, resp);
  }
}
