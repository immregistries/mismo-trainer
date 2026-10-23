package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.User;
import org.yaml.snakeyaml.Yaml;

/**
 * Select Configuration: picks the configuration loaded into the session (and remembered as the
 * user's current one across logins). Templates and the user's recent picks are one click; a single
 * search box matches hash, world, or island (or a whole signature); pasting or uploading a script
 * -- Mismo YAML or a DAR-CLI JSON file -- saves it as a configuration and loads it.
 *
 * <p>One-click selection is just {@code ?configurationId=N}, handled for every page by
 * {@link HomeServlet#setup}.
 *
 * @author Nathan Bunker
 */
@MultipartConfig
public class WeightSetServlet extends HomeServlet {

  public static final String PARAM_MATCH_ITEM_ID = "matchItemId";
  public static final String PARAM_MATCH_SET_ID = "matchSetId";

  public static final String PARAM_CONFIGURATION_ID = "configurationId";
  public static final String PARAM_CONFIGURATION_SCRIPT = "configurationScript";
  public static final String PARAM_CONFIGURATION_FILE = "configurationFile";
  public static final String PARAM_SEARCH = "q";

  /** DAR-CLI's MPATconfig JSON carries the Mismo YAML as a string in this field. */
  private static final String DAR_CLI_CONFIGURATION_FIELD = "mismoPatientMatchingConfiguration";

  private static final int RECENT_LIMIT = 5;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());

    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }
      Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
      Integer loadedId = (Integer) session.getAttribute(ATTRIBUTE_CONFIGURATION_LOADED_ID);
      Configuration loaded = loadedId == null ? null : OrgScope.loadConfiguration(dataSession, loadedId, user);

      String message = req.getParameter(PARAM_MESSAGE);
      if (message == null && req.getParameter(PARAM_CONFIGURATION_ID) != null) {
        message = loaded != null && req.getParameter(PARAM_CONFIGURATION_ID).equals(String.valueOf(loadedId))
            ? "Loaded " + label(loaded) + "."
            : "That configuration was not found.";
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-stack\">");
      out.println("      <h1 class=\"aira-page-title\">Select Configuration</h1>");

      printLoaded(out, loaded, user);

      out.println("      <div class=\"config-pick-columns\">");
      printPickList(out, "Templates", "Shared with every organization.",
          OrgScope.listTemplateConfigurations(dataSession), loadedId, "No templates yet.");
      printPickList(out, "Recent", "The last " + RECENT_LIMIT + " you loaded.",
          OrgScope.listRecentConfigurations(dataSession, user, RECENT_LIMIT), loadedId,
          "Nothing yet. Configurations you load will show up here.");
      out.println("      </div>");

      printSearch(out, req.getParameter(PARAM_SEARCH), dataSession, user, loadedId);
      printManualLoad(out);

      out.println("    </div>");
      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      e.printStackTrace(out);
    } finally {
      teardown(req, resp);
    }
    out.close();
  }

  /** Saves a pasted or uploaded script as a configuration, loads it, and redirects back. */
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    if (user == null) {
      teardown(req, resp);
      resp.sendRedirect("HomeServlet");
      return;
    }
    Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
    String message;
    Transaction transaction = null;
    try {
      String script = readSubmittedScript(req);
      transaction = dataSession.beginTransaction();
      OrgScope.ImportedConfiguration imported = OrgScope.importConfiguration(dataSession, script, user);
      transaction.commit();
      selectConfiguration(session, dataSession, imported.configuration(), user);
      message = (imported.added() ? "Saved and loaded " : "Already saved, so loaded the existing ")
          + label(imported.configuration()) + ".";
    } catch (Exception e) {
      if (transaction != null && transaction.isActive()) {
        transaction.rollback();
      }
      message = "Unable to load that configuration: " + (e.getMessage() == null ? e.toString() : e.getMessage());
    } finally {
      teardown(req, resp);
    }
    resp.sendRedirect("WeightSetServlet?" + PARAM_MESSAGE + "=" + URLEncoder.encode(message, "UTF-8"));
  }

  /**
   * The uploaded file wins over the textarea when both are given. Either may be Mismo YAML or a
   * DAR-CLI JSON file; JSON is unwrapped to its {@value #DAR_CLI_CONFIGURATION_FIELD} value.
   */
  private static String readSubmittedScript(HttpServletRequest req) throws IOException, ServletException {
    String text = null;
    Part filePart = req.getPart(PARAM_CONFIGURATION_FILE);
    if (filePart != null && filePart.getSize() > 0) {
      try (InputStream in = filePart.getInputStream()) {
        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    if (text == null || text.trim().isEmpty()) {
      text = req.getParameter(PARAM_CONFIGURATION_SCRIPT);
    }
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("paste a script or choose a file first.");
    }
    if (text.startsWith("﻿")) {
      text = text.substring(1);
    }
    if (text.trim().startsWith("{")) {
      // JSON is YAML, except that YAML forbids tab indentation; tabs inside JSON strings are always
      // escaped as \t, so any literal tab is whitespace and safe to replace.
      Object parsed = new Yaml().load(text.replace('\t', ' '));
      Object script = parsed instanceof Map ? ((Map<?, ?>) parsed).get(DAR_CLI_CONFIGURATION_FIELD) : null;
      if (!(script instanceof String)) {
        throw new IllegalArgumentException(
            "the JSON has no \"" + DAR_CLI_CONFIGURATION_FIELD + "\" field. Is it a DAR-CLI MPATconfig file?");
      }
      text = (String) script;
    }
    return text;
  }

  private static void printLoaded(PrintWriter out, Configuration loaded, User user) {
    out.println("      <section class=\"aira-panel\">");
    out.println("        <h2 class=\"aira-panel__title\">Loaded now</h2>");
    if (loaded == null) {
      out.println("        <p class=\"aira-muted\">No configuration is loaded. Pick a template or a recent one"
          + " below.</p>");
      out.println("      </section>");
      return;
    }
    out.println("        <p class=\"config-loaded__name\">" + escapeHtml(label(loaded)) + " " + ownerTag(loaded, user)
        + "</p>");
    out.println("        <p>" + details(loaded) + "</p>");
    out.println("        <p><a class=\"aira-button aira-button--secondary aira-button--small\" href=\"EvaluationServlet?"
        + PARAM_CONFIGURATION_ID + "=" + loaded.getConfigurationId() + "\">Evaluate</a></p>");
    out.println("        <details>");
    out.println("          <summary>View script</summary>");
    out.println("          <pre class=\"config-script\">" + escapeHtml(loaded.getConfigurationScript()) + "</pre>");
    out.println("        </details>");
    out.println("      </section>");
  }

  private static void printPickList(PrintWriter out, String title, String description, List<Configuration> list,
      Integer loadedId, String emptyText) {
    out.println("        <section class=\"aira-panel\">");
    out.println("          <h2 class=\"aira-panel__title\">" + escapeHtml(title) + "</h2>");
    out.println("          <p class=\"aira-muted\">" + escapeHtml(description) + "</p>");
    if (list.isEmpty()) {
      out.println("          <p class=\"aira-muted\">" + escapeHtml(emptyText) + "</p>");
    } else {
      out.println("          <ul class=\"config-pick-list\">");
      for (Configuration configuration : list) {
        boolean isLoaded = loadedId != null && loadedId == configuration.getConfigurationId();
        out.println("            <li>");
        if (isLoaded) {
          out.println("              <span class=\"config-pick config-pick--loaded\" aria-current=\"true\">");
        } else {
          out.println("              <a class=\"config-pick\" href=\"WeightSetServlet?" + PARAM_CONFIGURATION_ID + "="
              + configuration.getConfigurationId() + "\">");
        }
        out.println("                <span class=\"config-pick__name\">" + escapeHtml(label(configuration)) + "</span>");
        out.println("                <span class=\"config-pick__meta\">" + details(configuration) + "</span>");
        out.println("                <span class=\"config-pick__action\">" + (isLoaded ? "Loaded" : "Load") + "</span>");
        out.println(isLoaded ? "              </span>" : "              </a>");
        out.println("            </li>");
      }
      out.println("          </ul>");
    }
    out.println("        </section>");
  }

  private static void printSearch(PrintWriter out, String search, Session dataSession, User user, Integer loadedId) {
    out.println("      <section class=\"aira-panel\">");
    out.println("        <h2 class=\"aira-panel__title\">Find</h2>");
    out.println("        <form class=\"config-search\" action=\"WeightSetServlet\" method=\"GET\" role=\"search\">");
    out.println("          <label class=\"aira-visually-hidden\" for=\"config-search-input\">Search configurations</label>");
    out.println("          <input class=\"aira-input\" id=\"config-search-input\" type=\"search\" name=\"" + PARAM_SEARCH
        + "\" value=\"" + escapeHtml(search) + "\" placeholder=\"Hash, world, island, or a whole signature\"/>");
    out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
    out.println("        </form>");
    if (search != null && !search.trim().isEmpty()) {
      List<Configuration> results = OrgScope.searchConfigurations(dataSession, search, user);
      if (results.isEmpty()) {
        out.println("        <p class=\"aira-muted\">No configurations match &quot;" + escapeHtml(search.trim())
            + "&quot;.</p>");
      } else {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        DecimalFormat decimalFormat = new DecimalFormat("#0.0");
        out.println("        <p class=\"aira-muted\">" + results.size() + (results.size() == 1 ? " match" : " matches")
            + "</p>");
        out.println("        <div class=\"aira-table-wrap\">");
        out.println("        <table class=\"aira-table\">");
        out.println("          <thead><tr><th>World</th><th>Island</th><th>Generation</th><th>Score</th><th>Hash</th>"
            + "<th>Owner</th><th>Generated</th><th></th></tr></thead>");
        out.println("          <tbody>");
        for (Configuration configuration : results) {
          boolean isLoaded = loadedId != null && loadedId == configuration.getConfigurationId();
          out.println("            <tr>");
          out.println("              <td>" + escapeHtml(configuration.getWorldName()) + "</td>");
          out.println("              <td>" + escapeHtml(configuration.getIslandName()) + "</td>");
          out.println("              <td class=\"aira-table__cell--numeric\">" + configuration.getGeneration() + "</td>");
          out.println("              <td class=\"aira-table__cell--numeric\">"
              + decimalFormat.format(configuration.getGenerationScore() * 100.0) + "</td>");
          out.println("              <td class=\"aira-table__cell--code\">" + escapeHtml(configuration.getHashForSignature())
              + "</td>");
          out.println("              <td>" + ownerTag(configuration, user) + "</td>");
          out.println("              <td>" + sdf.format(configuration.getGeneratedDate()) + "</td>");
          out.println("              <td class=\"aira-table__cell--actions\">" + (isLoaded ? "Loaded"
              : "<a class=\"aira-button aira-button--secondary aira-button--small\" href=\"WeightSetServlet?"
                  + PARAM_CONFIGURATION_ID + "=" + configuration.getConfigurationId() + "\">Load</a>") + "</td>");
          out.println("            </tr>");
        }
        out.println("          </tbody>");
        out.println("        </table>");
        out.println("        </div>");
      }
    }
    out.println("      </section>");
  }

  private static void printManualLoad(PrintWriter out) {
    out.println("      <details class=\"aira-panel\">");
    out.println("        <summary class=\"aira-panel__title\">Load from script or file</summary>");
    out.println("        <p class=\"aira-muted\">Paste a Mismo configuration (YAML), or upload a .yml file or a"
        + " DAR-CLI MPATconfig .json file. It is saved to your organization and loaded. If the same configuration"
        + " (same hash) is already saved, that one is loaded instead.</p>");
    out.println("        <form class=\"aira-stack\" action=\"WeightSetServlet\" method=\"POST\""
        + " enctype=\"multipart/form-data\">");
    out.println("          <label class=\"aira-label\" for=\"config-script\">Configuration script</label>");
    out.println("          <textarea class=\"aira-textarea config-script-input\" id=\"config-script\" name=\""
        + PARAM_CONFIGURATION_SCRIPT + "\" rows=\"12\" wrap=\"off\"></textarea>");
    out.println("          <label class=\"aira-label\" for=\"config-file\">Or upload a file</label>");
    out.println("          <input id=\"config-file\" type=\"file\" name=\"" + PARAM_CONFIGURATION_FILE
        + "\" accept=\".yml,.yaml,.json,.txt\"/>");
    out.println("          <p><button class=\"aira-button aira-button--primary\" type=\"submit\">Save and load</button></p>");
    out.println("        </form>");
    out.println("      </details>");
  }

  private static String label(Configuration configuration) {
    return configuration.getWorldName() + " / " + configuration.getIslandName();
  }

  /** Generation, score, and hash, as one already-escaped line. */
  private static String details(Configuration configuration) {
    DecimalFormat decimalFormat = new DecimalFormat("#0.0");
    return "Generation " + configuration.getGeneration() + " · Score "
        + decimalFormat.format(configuration.getGenerationScore() * 100.0) + " · <code>"
        + escapeHtml(configuration.getHashForSignature()) + "</code>";
  }

  private static String ownerTag(Configuration configuration, User user) {
    if (configuration.isTemplate()) {
      return "<span class=\"aira-tag aira-tag--info\">Template</span>";
    }
    return OrgScope.isEditable(configuration, user) ? "<span class=\"aira-tag\">Your organization</span>"
        : "<span class=\"aira-tag aira-tag--outline\">" + escapeHtml(configuration.getOrganization().getName())
            + "</span>";
  }
}
