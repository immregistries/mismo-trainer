package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.StringUtils;
import org.immregistries.mismo.trainer.Island;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.IslandCredential;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.model.World;

/**
 * This is the central servlet that the remote island threads access to read and
 * store their data and report on progress. {@code doPost} is the machine-to-machine Island
 * sync API (database-schema-migration-plan.md §2.6/§3.6): every request must present a valid,
 * non-revoked {@code island_credential} token, which determines the organization all reads and
 * writes are scoped to -- never a client-supplied parameter.
 *
 * @author Nathan Bunker
 *
 */
public class CentralServlet extends HomeServlet {

  public static final String PARAM_ACTION = "action";
  public static final String PARAM_CONFIGURATION_SCRIPT = "configurationScript";
  public static final String PARAM_WORLD_NAME = "worldName";
  public static final String PARAM_ISLAND_NAME = "islandName";
  public static final String PARAM_CREDENTIAL = "credential";

  public static final String ACTION_UPDATE = "update";
  public static final String ACTION_QUERY = "query";
  public static final String ACTION_REQUEST_START_SCRIPT = "requestStartScript";

  public static final String RESULT_NOT_FOUND = "Not Found";
  public static final String RESULT_UNAUTHORIZED = "Unauthorized";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute("user");
    Session dataSession = (Session) session.getAttribute("dataSession");

    if (user == null) {
      RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
      dispatcher.forward(req, resp);
      return;
    }

    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HomeServlet.doHeader(out, req, user, null);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("    <h1 class=\"aira-page-title\">Central Servlet</h1>");
      out.println(
          "    <p><a href=\"IslandCredentialServlet\">Manage Island Credentials</a> -- Island processes need one"
              + " of these to sync with this server.</p>");

      DecimalFormat decimalFormat = new DecimalFormat("#0.0");

      List<Configuration> configurationList = OrgScope.listConfigurations(dataSession, user);

      out.println("<table border=\"1\" cellspacing=\"0\">");
      out.println("  <tr>");
      out.println("    <th>World</th>");
      out.println("    <th>Island</th>");
      out.println("    <th>Signature</th>");
      out.println("    <th>Generation</th>");
      out.println("    <th>Score</th>");
      out.println("    <th>Select</th>");
      out.println("  </tr>");
      for (Configuration configuration : configurationList) {
        out.println("      <tr>");
        out.println("        <td>" + configuration.getWorldName() + "</td>");
        out.println("        <td>" + configuration.getIslandName() + "</td>");
        out.println("        <td>" + configuration.getHashForSignature() + "</td>");
        out.println("        <td>" + configuration.getGeneration() + "</td>");
        out.println("        <td>" + decimalFormat.format((configuration.getGenerationScore() * 100.0)) + "</td>");
        out.println("        <td>");
        out.println("          <form action=\"WeightSetServlet\" method=\"GET\"> ");
        out.println("            <input type=\"hidden\" name=\"" + WeightSetServlet.PARAM_CONFIGURATION_ID
            + "\" value=\"" + configuration.getConfigurationId() + "\"/>");
        out.println("          <input type=\"submit\" name=\"submit\" value=\"Select\"/>");
        out.println("          </form>");
        out.println("        </td>");
        out.println("      </tr>");
      }
      out.println("    </table>");
      // create a form that allows posting actions to this same servlet
      // first form is for the requestStartScript action
      // will need to have a text entry field for the world name and island name, then
      // a submit button, named action
      // with a value of ACTION_REQUEST_START_SCRIPT
      out.println("    <h2>Request Start Script</h2>");
      out.println("    <form action=\"CentralServlet\" method=\"POST\">");
      out.println("        <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_REQUEST_START_SCRIPT
          + "\"/>");
      out.println("        <label for=\"" + PARAM_WORLD_NAME + "\">World Name:</label>");
      out.println("        <input type=\"text\" name=\"" + PARAM_WORLD_NAME + "\" id=\"" + PARAM_WORLD_NAME + "\"/>");
      out.println("        <label for=\"" + PARAM_ISLAND_NAME + "\">Island Name:</label>");
      out.println("        <input type=\"text\" name=\"" + PARAM_ISLAND_NAME + "\" id=\"" + PARAM_ISLAND_NAME + "\"/>");
      out.println("        <input type=\"submit\" name=\"submit\" value=\"Request Start Script\"/>");
      out.println("    </form>");
      out.println("   <h2>Last Configuration Script Received</h2>");
      out.println("   <pre>" + lastConfigurationScriptReceived + "</pre>");
      out.println("    </div>");
      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      out.print("<pre>");
      e.printStackTrace(out);
      out.print("</pre>");
    } finally {
      out.close();
      teardown(req, resp);
    }
  }

  private static String lastConfigurationScriptReceived = null;

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    getSessionFactory();
    Session dataSession = factory.openSession();

    String worldName = req.getParameter(PARAM_WORLD_NAME);
    String islandName = req.getParameter(PARAM_ISLAND_NAME);
    try {
      IslandCredential credential = IslandCredentialSupport.resolve(dataSession, req.getParameter(PARAM_CREDENTIAL));
      if (credential == null) {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        out.println(RESULT_UNAUTHORIZED);
        out.close();
        return;
      }
      Organization organization = credential.getOrganization();
      IslandCredentialSupport.touchLastUsed(dataSession, credential);

      String action = req.getParameter(PARAM_ACTION);
      if (action == null) {
        action = ACTION_UPDATE;
      }
      if (action.equals(ACTION_UPDATE)) {
        String configurationScript = req.getParameter(PARAM_CONFIGURATION_SCRIPT);
        lastConfigurationScriptReceived = configurationScript;
        Configuration configuration = getLatestConfiguration(dataSession, worldName, islandName, organization);
        if (configuration == null) {
          // Never-before-seen (worldName, islandName) pair for this organization -- generated_date
          // is NOT NULL, so it must be set here rather than left to its Java default.
          // (known-issues.md; v2-roadmap.md §6)
          configuration = new Configuration();
          configuration.setGeneratedDate(new Date());
          configuration.setCreatedAt(new Date());
          configuration.setOrganization(organization);
        }
        org.immregistries.mismo.match.model.Configuration matchConfiguration =
            new org.immregistries.mismo.match.model.Configuration();
        matchConfiguration.setConfigurationScript(configurationScript);
        matchConfiguration.setup();
        configuration.setConfigurationScript(matchConfiguration.getConfigurationScript());
        configuration.setHashForSignature(matchConfiguration.getHashForSignature());
        configuration.setWorldName(worldName);
        configuration.setIslandName(islandName);
        configuration.setIslandCredential(credential);

        Transaction transaction = dataSession.beginTransaction();
        dataSession.save(configuration);
        transaction.commit();

        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        out.println("OK");
        out.close();
      } else if (action.equals(ACTION_QUERY)) {
        Configuration configuration = getLatestConfiguration(dataSession, worldName, islandName, organization);
        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        if (configuration == null) {
          out.println(RESULT_NOT_FOUND);
        } else {
          out.println(configuration.getConfigurationScript());
        }
        out.close();
      } else if (action.equals(ACTION_REQUEST_START_SCRIPT)) {
        Configuration configuration = null;
        if (StringUtils.isNotEmpty(islandName)) {
          configuration = getLatestConfiguration(dataSession, worldName, islandName, organization);
        }
        if (configuration == null) {
          // Cross-island seeding within the same organization: fall back to the best result
          // from any sibling island in this worldName, still scoped to this credential's org.
          Query query = dataSession.createQuery(
              "from Configuration where worldName = :worldName and organization = :organization"
                  + " order by generation desc");
          query.setParameter("worldName", worldName);
          query.setParameter("organization", organization);
          List<Configuration> configurationList = query.list();
          if (configurationList.size() > 0) {
            configuration = configurationList.get(0);
          } else {
            configuration = new Configuration();
          }
        }
        resp.setContentType("text/plain");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        out.println(configuration.getConfigurationScript() == null ? "" : configuration.getConfigurationScript());
        out.close();
      }
    } finally {
      dataSession.close();
    }
  }

  private Configuration getLatestConfiguration(Session dataSession, String worldName, String islandName,
      Organization organization) {
    Configuration configuration = null;
    Query query = dataSession.createQuery(
        "from Configuration where worldName = :worldName and islandName = :islandName"
            + " and organization = :organization order by generation desc");
    query.setParameter("worldName", worldName);
    query.setParameter("islandName", islandName);
    query.setParameter("organization", organization);
    List<Configuration> configurationList = query.list();
    if (configurationList.size() > 0) {
      configuration = configurationList.get(0);
    }
    return configuration;
  }

}
