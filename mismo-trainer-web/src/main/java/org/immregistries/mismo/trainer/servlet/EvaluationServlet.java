package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.Evaluation;
import org.immregistries.mismo.trainer.model.EvaluationResult;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.User;

/**
 * Runs and persists Evaluations -- scoring a Test Set against a Configuration and keeping the
 * result as a durable, queryable record instead of computing it live and discarding it
 * (v2-roadmap.md §11; database-changes-for-functional-model.md §5). Combines: starting a new
 * evaluation, its summary/confusion-matrix/disagreement view, and the list of past evaluations for
 * a selected Test Set or Configuration -- one page, following this project's existing
 * list-plus-detail servlet convention ({@code TestSetServlet}).
 */
public class EvaluationServlet extends HomeServlet {

  public static final String PARAM_MATCH_SET_ID = TestSetServlet.PARAM_MATCH_SET_ID;
  public static final String PARAM_CONFIGURATION_ID = HomeServlet.PARAM_CONFIGURATION_ID;
  public static final String PARAM_EVALUATION_ID = "evaluationId";

  public static final String ACTION_RUN_EVALUATION = "Run Evaluation";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }
      Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
      String message = null;

      MatchSet matchSetSelected = null;
      if (req.getParameter(PARAM_MATCH_SET_ID) != null) {
        matchSetSelected = OrgScope.loadMatchSet(dataSession, Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID)),
            user);
      }
      Configuration configurationSelected = null;
      if (req.getParameter(PARAM_CONFIGURATION_ID) != null) {
        configurationSelected = OrgScope.loadConfiguration(dataSession,
            Integer.parseInt(req.getParameter(PARAM_CONFIGURATION_ID)), user);
      }

      Evaluation evaluationSelected = null;
      String action = req.getParameter(PARAM_ACTION);
      if (ACTION_RUN_EVALUATION.equals(action)) {
        if (matchSetSelected != null && configurationSelected != null) {
          Transaction transaction = dataSession.beginTransaction();
          evaluationSelected = OrgScope.runEvaluation(dataSession, matchSetSelected, configurationSelected, user);
          transaction.commit();
          message = "Evaluation #" + evaluationSelected.getEvaluationId() + " complete.";
        } else {
          message = "Select both a Test Set and a Configuration before running an evaluation.";
        }
      } else if (req.getParameter(PARAM_EVALUATION_ID) != null) {
        evaluationSelected = OrgScope.loadEvaluation(dataSession,
            Integer.parseInt(req.getParameter(PARAM_EVALUATION_ID)), user);
        if (evaluationSelected != null) {
          matchSetSelected = evaluationSelected.getMatchSet();
          configurationSelected = evaluationSelected.getConfiguration();
        }
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("    <h1 class=\"aira-page-title\">Evaluations</h1>");

      printRunForm(out, dataSession, user, matchSetSelected, configurationSelected);

      if (evaluationSelected != null) {
        printSummary(out, dataSession, evaluationSelected);
      }

      if (matchSetSelected != null) {
        out.println("    <h2>Past Evaluations for &quot;" + escapeHtml(matchSetSelected.getLabel()) + "&quot;</h2>");
        printEvaluationList(out, OrgScope.listEvaluationsForMatchSet(dataSession, matchSetSelected, user),
            matchSetSelected);
      } else if (configurationSelected != null) {
        out.println("    <h2>Past Evaluations for &quot;" + escapeHtml(configurationSelected.getWorldName()) + " / "
            + escapeHtml(configurationSelected.getIslandName()) + "&quot;</h2>");
        printEvaluationList(out, OrgScope.listEvaluationsForConfiguration(dataSession, configurationSelected, user),
            null);
      }

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

  private void printRunForm(PrintWriter out, Session dataSession, User user, MatchSet matchSetSelected,
      Configuration configurationSelected) {
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Start an Evaluation</h2>");
    out.println("      <form action=\"EvaluationServlet\" method=\"POST\">");
    out.println("        <table>");
    out.println("          <tr><td valign=\"top\">Test Set</td><td><select name=\"" + PARAM_MATCH_SET_ID + "\">");
    for (MatchSet matchSet : OrgScope.listMatchSets(dataSession, user)) {
      boolean selected = matchSetSelected != null && matchSetSelected.equals(matchSet);
      out.println("            <option value=\"" + matchSet.getMatchSetId() + "\""
          + (selected ? " selected=\"true\"" : "") + ">" + escapeHtml(matchSet.getLabel()) + "</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td valign=\"top\">Configuration</td><td><select name=\"" + PARAM_CONFIGURATION_ID
        + "\">");
    for (Configuration configuration : OrgScope.listConfigurations(dataSession, user)) {
      boolean selected = configurationSelected != null
          && configurationSelected.getConfigurationId() == configuration.getConfigurationId();
      out.println("            <option value=\"" + configuration.getConfigurationId() + "\""
          + (selected ? " selected=\"true\"" : "") + ">" + escapeHtml(configuration.getWorldName()) + " / "
          + escapeHtml(configuration.getIslandName()) + " (gen " + configuration.getGeneration() + ")</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
        + "\" value=\"" + ACTION_RUN_EVALUATION + "\"/></td></tr>");
    out.println("        </table>");
    out.println("      </form>");
    out.println("    </section>");
  }

  private void printSummary(PrintWriter out, Session dataSession, Evaluation evaluation) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    DecimalFormat decimalFormat = new DecimalFormat("#0.00");
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Evaluation #" + evaluation.getEvaluationId() + " Summary</h2>");
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Test Set</th><td>" + escapeHtml(evaluation.getMatchSet().getLabel()) + "</td></tr>");
    out.println("        <tr><th>Configuration</th><td>" + escapeHtml(evaluation.getConfiguration().getWorldName())
        + " / " + escapeHtml(evaluation.getConfiguration().getIslandName()) + " (gen "
        + evaluation.getConfiguration().getGeneration() + ")</td></tr>");
    out.println("        <tr><th>Run By</th><td>" + (evaluation.getRunByUser() == null ? ""
        : escapeHtml(evaluation.getRunByUser().getDisplayName() != null ? evaluation.getRunByUser().getDisplayName()
            : evaluation.getRunByUser().getEmail())) + "</td></tr>");
    out.println("        <tr><th>Run At</th><td>" + sdf.format(evaluation.getRunAt()) + "</td></tr>");
    out.println("        <tr><th>Total Cases</th><td>" + evaluation.getTotalCases() + "</td></tr>");
    out.println("        <tr><th>Scorable Cases</th><td>" + evaluation.getScorableCases() + "</td></tr>");
    out.println("        <tr><th>Not Sure Cases</th><td>" + evaluation.getNotSureCases() + "</td></tr>");
    out.println("        <tr><th>Agree</th><td>" + evaluation.getAgreeCount() + "</td></tr>");
    out.println("        <tr><th>Disagree</th><td>" + evaluation.getDisagreeCount() + "</td></tr>");
    out.println("        <tr><th>Score</th><td>" + (evaluation.getScore() == null ? "n/a"
        : decimalFormat.format(evaluation.getScore() * 100.0) + "%") + "</td></tr>");
    out.println("      </table>");

    printConfusionMatrix(out, OrgScope.confusionMatrix(dataSession, evaluation));

    List<EvaluationResult> disagreements = OrgScope.listDisagreements(dataSession, evaluation);
    out.println("      <h3>Disagreements (" + disagreements.size() + ")</h3>");
    if (disagreements.isEmpty()) {
      out.println("      <p>No disagreements -- every scorable case agreed with its expectation.</p>");
    } else {
      out.println("      <table border=\"1\" cellspacing=\"0\">");
      out.println("        <tr><th>Test Case</th><th>Expected</th><th>Calculated</th><th>Signature</th></tr>");
      for (EvaluationResult result : disagreements) {
        String link = "TestSetServlet?" + TestSetServlet.PARAM_MATCH_SET_ID + "="
            + evaluation.getMatchSet().getMatchSetId() + "&" + TestSetServlet.PARAM_MATCH_ITEM_ID + "="
            + result.getMatchItem().getMatchItemId();
        out.println("        <tr>");
        out.println("          <td class=\"fail\"><a href=\"" + link + "\">"
            + escapeHtml(result.getMatchItem().getLabel()) + "</a></td>");
        out.println("          <td class=\"fail\">" + escapeHtml(result.getExpectedClassification()) + "</td>");
        out.println("          <td class=\"fail\">" + escapeHtml(result.getCalculatedClassification()) + "</td>");
        out.println("          <td class=\"fail\">" + (result.getSignature() == null ? ""
            : escapeHtml(result.getSignature())) + "</td>");
        out.println("        </tr>");
      }
      out.println("      </table>");
    }
    out.println("    </section>");
  }

  /** Renders the confusion matrix derived from {@code EvaluationResult} (never stored directly). */
  static void printConfusionMatrix(PrintWriter out, Map<String, Map<String, Long>> matrix) {
    out.println("      <h3>Confusion Matrix</h3>");
    if (matrix.isEmpty()) {
      out.println("      <p>No results.</p>");
      return;
    }
    TreeSet<String> calculatedValues = new TreeSet<String>();
    for (Map<String, Long> byCalculated : matrix.values()) {
      calculatedValues.addAll(byCalculated.keySet());
    }
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Expected \\ Calculated</th>");
    for (String calculated : calculatedValues) {
      out.println("          <th>" + escapeHtml(calculated) + "</th>");
    }
    out.println("        </tr>");
    for (Map.Entry<String, Map<String, Long>> row : matrix.entrySet()) {
      out.println("        <tr>");
      out.println("          <th>" + escapeHtml(row.getKey()) + "</th>");
      for (String calculated : calculatedValues) {
        Long count = row.getValue().get(calculated);
        out.println("          <td>" + (count == null ? 0 : count) + "</td>");
      }
      out.println("        </tr>");
    }
    out.println("      </table>");
  }

  private void printEvaluationList(PrintWriter out, List<Evaluation> evaluations, MatchSet matchSetContext) {
    if (evaluations.isEmpty()) {
      out.println("    <p>No evaluations yet.</p>");
      return;
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    DecimalFormat decimalFormat = new DecimalFormat("#0.00");
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Run At</th><th>Test Set</th><th>Configuration</th><th>Agree/Scorable</th>"
        + "<th>Score</th><th>&nbsp;</th></tr>");
    for (Evaluation evaluation : evaluations) {
      String link = "EvaluationServlet?" + PARAM_EVALUATION_ID + "=" + evaluation.getEvaluationId();
      if (matchSetContext != null) {
        link += "&" + PARAM_MATCH_SET_ID + "=" + matchSetContext.getMatchSetId();
      }
      out.println("      <tr>");
      out.println("        <td>" + sdf.format(evaluation.getRunAt()) + "</td>");
      out.println("        <td>" + escapeHtml(evaluation.getMatchSet().getLabel()) + "</td>");
      out.println("        <td>" + escapeHtml(evaluation.getConfiguration().getWorldName()) + " / "
          + escapeHtml(evaluation.getConfiguration().getIslandName()) + "</td>");
      out.println("        <td>" + evaluation.getAgreeCount() + " / " + evaluation.getScorableCases() + "</td>");
      out.println("        <td>" + (evaluation.getScore() == null ? "n/a"
          : decimalFormat.format(evaluation.getScore() * 100.0) + "%") + "</td>");
      out.println("        <td><a href=\"" + link + "\">View</a></td>");
      out.println("      </tr>");
    }
    out.println("    </table>");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doGet(req, resp);
  }
}
