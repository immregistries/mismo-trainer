package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.immregistries.aira.web.AiraAccountConfig;
import org.immregistries.aira.web.AiraContextConfig;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraNavigationItem;
import org.immregistries.aira.web.AiraPage;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.trainer.SoftwareVersion;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.User;

/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well
 * the weights work.
 *
 * @author Nathan Bunker
 */
public class HomeServlet extends HttpServlet {
  public static final String PARAM_MESSAGE = "message";
  public static final String PARAM_CONFIGURATION_ID = "configurationId";

  public static final String PARAM_ACTION = "action";
  public static final String ATTRIBUTE_USER = "user";
  public static final String ATTRIBUTE_DATA_SESSION = "dataSession";

  public static final String ATTRIBUTE_PATIENT_COMPARE = "patientCompare";
  public static final String ATTRIBUTE_MATCH_TEST_CASE_LIST = "matchTestCaseList";

  private static final String APPLICATION_NAME = "Mismo Trainer";
  private static final String APPLICATION_SUBTITLE = "MISMO patient matching test and tuning";

  protected void setup(HttpServletRequest req, HttpServletResponse resp) {
    HttpSession session = req.getSession(true);
    Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
    if (dataSession != null) {
      dataSession.close();
    }
    getSessionFactory();
    dataSession = factory.openSession();
    session.setAttribute(ATTRIBUTE_DATA_SESSION, dataSession);
    if (req.getParameter(PARAM_CONFIGURATION_ID) != null) {
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      Configuration configuration = OrgScope.loadConfiguration(dataSession,
          Integer.parseInt(req.getParameter(PARAM_CONFIGURATION_ID)), user);
      if (configuration != null) {
        PatientCompare patientCompare = new PatientCompare(configuration.getConfigurationScript());
        session.setAttribute(ATTRIBUTE_PATIENT_COMPARE, patientCompare);
      }
    }

  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      setup(req, resp);
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      String message = req.getParameter(PARAM_MESSAGE);

      doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("      <h1 class=\"aira-page-title\">Mismo Match Toolset</h1>");
      if (user == null) {
        // Unreachable in practice: AuthenticationFilter redirects to InteropHub
        // login before any request reaches this servlet with no session user.
        out.println("      <p>You must log in via InteropHub to use Mismo Match.</p>");
      } else {
        out.println("      <div class=\"aira-grid\">");
        out.println("        <section class=\"aira-panel\">");
        out.println("          <h2 class=\"aira-panel__title\">Primary Tools</h2>");
        out.println("          <ul>");
        out.println(
            "            <li><a href=\"CentralServlet\">Central</a>: Shows the status of the central server"
                + " that is responsible for listening to remote Island processes and reporting on"
                + " the progress of these optimizations.</li>");
        out.println(
            "            <li><a href=\"WeightSetServlet\">Configuration</a>: Allows for viewing and updating the"
                + " currently selected weight set.</li>");
        out.println(
            "            <li><a href=\"TestSetServlet\">Test Set</a>: Allows for entry and management of"
                + " test sets.</li>");
        out.println(
            "            <li><a href=\"ReviewServlet\">Review</a>: Review tests that fail in context of"
                + " similar tests. </li>");
        out.println("            <li><a href=\"logout\">Logout</a></li>");
        out.println("          </ul>");
        out.println("        </section>");
        out.println("        <section class=\"aira-panel\">");
        out.println("          <h2 class=\"aira-panel__title\">Other Tools</h2>");
        out.println("          <ul>");
        out.println(
            "            <li><a href=\"TestMatchingServlet\">Test Matching</a>: Shows the results of how"
                + " well a particular matching set works.</li>");
        out.println(
            "            <li><a href=\"MatchPatientServlet\">Match Patient</a>: Shows how a single patient"
                + " is matched using the weighting system.</li>");
        out.println("            <li><a href=\"ConvertDataServlet\">Convert Data to Match Format</a></li>");
        out.println(
            "            <li><a href=\"AddressTestServlet\">Address Test</a>: Allows for looking at how"
                + " addresses are read.</li>");
        out.println(
            "            <li><a href=\"GenerateWeightsServlet\">Generate Weights</a>: Starts evolutionary"
                + " algorithm that hunts for best weights. Do not click unless you are ready for"
                + " generator start.</li>");
        out.println(
            "            <li><a href=\"RandomServlet\">Random</a>: Supports creating a set of three random"
                + " patients, the second matching with the first and the third having similar"
                + " characteristics but not being a match.</li>");
        out.println(
            "            <li><a href=\"RandomScriptServlet\">Random Script</a>: Creates script with lots of"
                + " example data.</li>");
        out.println(
            "            <li><a href=\"RandomForCDCServlet\">Random for CDC Servlet</a>:  Creates data in a"
                + " spreadsheet that was requested by the CDC deduplication project.</li>");
        out.println(
            "            <li><a href=\"ExampleServlet\">Example Servlet</a>:  Compares two inputs with JaroWinkler</li>");
        out.println(
            "            <li><a href=\"MatchNodeServlet\">Match Node Servlet</a>:  Shows operation of single match node</li>");
        out.println("          </ul>");
        out.println("        </section>");
        out.println("      </div>");
      }
      out.println("    </div>");
      doFooter(out, req);
    } catch (Exception e) {
      e.printStackTrace(out);
    } finally {
      teardown(req, resp);
    }
    out.close();
  }

  /**
   * Writes the shared AIRA page shell (identity, account, contextual navigation) and opens
   * &lt;main&gt;. Everything printed after this call until {@link #doFooter} stays hand-written
   * HTML, same as v1.
   */
  public static void doHeader(PrintWriter out, HttpServletRequest req, User user, String message) {
    AiraPage page = buildPage(req, user).build();
    page.writeStart(out);
    if (message != null) {
      out.println("    <div class=\"aira-container--wide\">");
      out.println("      <div class=\"aira-alert aira-alert--warning\" role=\"alert\"><p>"
          + escapeHtml(message) + "</p></div>");
      out.println("    </div>");
    }
  }

  private static AiraPage.Builder buildPage(HttpServletRequest req, User user) {
    String activePath = req.getServletPath();
    AiraPage.Builder builder = AiraPage.builder()
        .applicationName(APPLICATION_NAME)
        .applicationSubtitle(APPLICATION_SUBTITLE)
        .applicationVersion(SoftwareVersion.VERSION)
        .documentTitle(APPLICATION_NAME)
        .contextPath(req.getContextPath())
        .identityHref("/HomeServlet")
        .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT))
        .addLocalStylesheet("/css/application.css");
    if (user != null) {
      String label = user.getDisplayName() == null ? user.getEmail() : user.getDisplayName();
      if (user.getOrganization() != null && user.getOrganization().getName() != null) {
        label = label + " · " + user.getOrganization().getName();
      }
      builder.account(new AiraAccountConfig(label, "Log out", "/logout"));
      builder.context(new AiraContextConfig(APPLICATION_NAME, List.of(
          new AiraNavigationItem("Central", "/CentralServlet", "/CentralServlet".equals(activePath)),
          new AiraNavigationItem("Configuration", "/WeightSetServlet", "/WeightSetServlet".equals(activePath)),
          new AiraNavigationItem("Test Set", "/TestSetServlet", "/TestSetServlet".equals(activePath)),
          new AiraNavigationItem("Review", "/ReviewServlet", "/ReviewServlet".equals(activePath)),
          new AiraNavigationItem("Signature", "/SignatureServlet", "/SignatureServlet".equals(activePath)))));
    } else {
      builder.account(new AiraAccountConfig("", "Log in", "/login"));
    }
    return builder;
  }

  /**
   * Closes out any content that must render inside &lt;main&gt; (the loaded-configuration panel)
   * and writes the shared AIRA footer/document end.
   */
  public static void doFooter(PrintWriter out, HttpServletRequest req) {
    PatientCompare patientCompare = (PatientCompare) req.getSession().getAttribute(ATTRIBUTE_PATIENT_COMPARE);
    if (patientCompare != null && patientCompare.getConfiguration() != null) {
      DecimalFormat decimalFormat = new DecimalFormat("#0.0");
      org.immregistries.mismo.match.model.Configuration c = patientCompare.getConfiguration();
      out.println("    <div class=\"aira-container--wide\">");
      out.println("      <section class=\"aira-panel\">");
      out.println("        <h2 class=\"aira-panel__title\">Configuration Loaded</h2>");
      out.println("        <table>");
      out.println("          <tr><th>World</th><td>" + escapeHtml(c.getWorldName()) + "</td></tr>");
      out.println("          <tr><th>Island</th><td>" + escapeHtml(c.getIslandName()) + "</td></tr>");
      out.println("          <tr><th>Signature</th><td>" + escapeHtml(c.getHashForSignature()) + "</td></tr>");
      out.println("          <tr><th>Score</th><td>" + decimalFormat.format((c.getGenerationScore() * 100.0))
          + "</td></tr>");
      out.println("        </table>");
      out.println("      </section>");
      out.println("    </div>");
    }
    AiraPage page = buildPage(req, (User) req.getSession().getAttribute(ATTRIBUTE_USER)).build();
    page.writeEnd(out);
  }

  protected static void teardown(HttpServletRequest req, HttpServletResponse resp) {
    HttpSession session = req.getSession(true);
    Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
    if (dataSession != null) {
      dataSession.close();
    }
    session.removeAttribute(ATTRIBUTE_DATA_SESSION);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  protected static SessionFactory factory;

  /**
   * Session factory singleton.
   */
  public static SessionFactory getSessionFactory() {
    if (factory == null) {
      factory = new AnnotationConfiguration().configure().buildSessionFactory();
    }
    return factory;
  }

  static String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
