package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        out.println("      <div class=\"aira-card-grid\">");
        for (NavArea area : NavArea.values()) {
          out.println("        <section class=\"aira-panel\">");
          out.println("          <h2 class=\"aira-panel__title\">" + escapeHtml(area.label) + "</h2>");
          out.println("          <p>" + escapeHtml(area.description) + "</p>");
          out.println("          <ul>");
          for (NavPage navPage : area.pages) {
            out.println("            <li><a href=\"" + navPage.path().substring(1) + "\">"
                + escapeHtml(navPage.label()) + "</a></li>");
          }
          out.println("          </ul>");
          out.println("        </section>");
        }
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
   * &lt;main&gt;. Row 1 (identity + account) and row 2 (the six {@link NavArea} entries, via
   * {@link #buildPage}'s context config) come from {@link AiraPage} itself. When the active page
   * belongs to a {@link NavArea}, this also opens the {@code aira-right-rail-layout} grid (the
   * area's own sub-pages render into its right-hand rail in {@link #doFooter}); pages outside any
   * area (e.g. Home) get a plain single-column container instead, since there is no per-area sub
   * page list to show. Everything printed after this call until {@link #doFooter} stays
   * hand-written HTML, same as v1.
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
    if (AREA_BY_PATH.get(req.getServletPath()) != null) {
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("      <div class=\"aira-right-rail-layout\">");
      out.println("        <div>");
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

      NavArea currentArea = AREA_BY_PATH.get(activePath);
      List<AiraNavigationItem> areaNavItems = new ArrayList<>();
      for (NavArea area : NavArea.values()) {
        areaNavItems.add(new AiraNavigationItem(area.label, area.primaryPath(), area == currentArea));
      }
      builder.context(new AiraContextConfig(APPLICATION_NAME, areaNavItems));
    } else {
      builder.account(new AiraAccountConfig("", "Log in", "/login"));
    }
    return builder;
  }

  /** One page/action listed in an area's right-side context navigation. */
  private record NavPage(String label, String path) {
  }

  /**
   * The five analyst-facing top-level navigation areas from the functional model, plus a
   * "Tools" catch-all for dev/diagnostic utility pages that don't fit any of the five
   * (v2-roadmap.md §9, ui-changes-for-functional-model.md Track A). Every area is always listed
   * in row 2's {@link AiraContextConfig} navigation (the active one highlighted); its
   * {@link #pages} list becomes the right-hand rail rendered by {@link #doFooter} whenever the
   * active page belongs to that area.
   */
  private enum NavArea {
    TEST_SETS("Test Sets", "Manage test sets, review test cases, and compare individual patient pairs.",
        List.of(
            new NavPage("Test Sets", "/TestSetServlet"),
            new NavPage("Compare a Pair", "/MatchPatientServlet"))),
    CONFIGURATIONS("Configurations",
        "View the current weight set or browse all configurations available to your organization.",
        List.of(
            new NavPage("Weight Set", "/WeightSetServlet"),
            new NavPage("Browse All Configurations", "/CentralServlet"))),
    EVALUATIONS("Evaluations", "Run test matching against a configuration and review the results.",
        List.of(
            new NavPage("Evaluations", "/EvaluationServlet"),
            new NavPage("Compare Configurations", "/EvaluationCompareServlet"),
            new NavPage("Test Matching", "/TestMatchingServlet"),
            new NavPage("Review", "/ReviewServlet"))),
    SIGNATURES("Signatures", "Inspect how a match signature scores against a configuration.",
        List.of(
            new NavPage("Signature Inspector", "/SignatureServlet"),
            new NavPage("Batch Signature Analysis", "/SignatureBatchServlet"))),
    OPTIMIZATION("Optimization", "Monitor Island optimization processes and manage their credentials.",
        List.of(
            new NavPage("Central / Island Sync", "/CentralServlet"),
            new NavPage("Island Credentials", "/IslandCredentialServlet"))),
    TOOLS("Tools", "Dev and diagnostic utilities for addresses, data conversion, and example data.",
        List.of(
            new NavPage("Address Test", "/AddressTestServlet"),
            new NavPage("Example", "/ExampleServlet"),
            new NavPage("Convert Data", "/ConvertDataServlet"),
            new NavPage("Match Node", "/MatchNodeServlet"),
            new NavPage("Explore Test Script", "/TestScriptExploreServlet"),
            new NavPage("Random", "/RandomServlet"),
            new NavPage("Random Script", "/RandomScriptServlet"),
            new NavPage("Random for CDC", "/RandomForCDCServlet")));

    final String label;
    final String description;
    final List<NavPage> pages;

    NavArea(String label, String description, List<NavPage> pages) {
      this.label = label;
      this.description = description;
      this.pages = pages;
    }

    String primaryPath() {
      return pages.get(0).path();
    }
  }

  /**
   * Reverse lookup from servlet path to the area whose top button/context title it activates.
   * {@code CentralServlet} is reachable from both Configurations (browse/select/copy weight
   * sets) and Optimization (Island sync monitoring) context navigation, but is only ever the
   * active/highlighted area under Optimization, since the page itself is fundamentally the
   * Island-facing infrastructure surface (v2-roadmap.md §9, functional model §2.9).
   */
  private static final Map<String, NavArea> AREA_BY_PATH = buildAreaByPath();

  private static Map<String, NavArea> buildAreaByPath() {
    Map<String, NavArea> map = new LinkedHashMap<>();
    map.put("/TestSetServlet", NavArea.TEST_SETS);
    map.put("/MatchPatientServlet", NavArea.TEST_SETS);
    map.put("/WeightSetServlet", NavArea.CONFIGURATIONS);
    map.put("/EvaluationServlet", NavArea.EVALUATIONS);
    map.put("/EvaluationCompareServlet", NavArea.EVALUATIONS);
    map.put("/TestMatchingServlet", NavArea.EVALUATIONS);
    map.put("/ReviewServlet", NavArea.EVALUATIONS);
    map.put("/SignatureServlet", NavArea.SIGNATURES);
    map.put("/SignatureBatchServlet", NavArea.SIGNATURES);
    map.put("/CentralServlet", NavArea.OPTIMIZATION);
    map.put("/IslandCredentialServlet", NavArea.OPTIMIZATION);
    map.put("/AddressTestServlet", NavArea.TOOLS);
    map.put("/ExampleServlet", NavArea.TOOLS);
    map.put("/ConvertDataServlet", NavArea.TOOLS);
    map.put("/MatchNodeServlet", NavArea.TOOLS);
    map.put("/TestScriptExploreServlet", NavArea.TOOLS);
    map.put("/RandomServlet", NavArea.TOOLS);
    map.put("/RandomScriptServlet", NavArea.TOOLS);
    map.put("/RandomForCDCServlet", NavArea.TOOLS);
    return Map.copyOf(map);
  }

  /**
   * Closes the right-rail-layout opened by {@link #doHeader} (rendering the current area's sub
   * pages into the right-hand rail along the way), closes out any content that must render
   * inside &lt;main&gt; (the loaded-configuration panel), and writes the shared AIRA
   * footer/document end.
   */
  public static void doFooter(PrintWriter out, HttpServletRequest req) {
    NavArea currentArea = AREA_BY_PATH.get(req.getServletPath());
    if (currentArea != null) {
      out.println("        </div>");
      out.println("        <aside class=\"aira-panel aira-sidebar\" aria-label=\""
          + escapeHtml(currentArea.label) + " navigation\">");
      out.println("          <nav class=\"aira-sidebar-nav\">");
      for (NavPage navPage : currentArea.pages) {
        boolean active = navPage.path().equals(req.getServletPath());
        out.println("            <a class=\"aira-sidebar-link\" href=\"" + navPage.path().substring(1) + "\""
            + (active ? " aria-current=\"page\"" : "") + ">" + escapeHtml(navPage.label()) + "</a>");
      }
      out.println("          </nav>");
      out.println("        </aside>");
      out.println("      </div>");
      out.println("    </div>");
    }
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
