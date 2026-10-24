package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.Evaluation;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.servlet.OrgScope.EvaluationComparison;
import org.immregistries.mismo.trainer.servlet.OrgScope.EvaluationComparisonCase;

/**
 * Configuration comparison: scores the same Test Set against two Configurations (A and B) and
 * shows which cases Improved, Regressed, Changed (still wrong, but differently), or stayed
 * Unchanged (database-changes-for-functional-model.md §5; v2-roadmap.md §11). Needs no schema of
 * its own -- it is entirely a query over two {@code Evaluation} runs, reused if a recent one
 * already exists for a given (Test Set, Configuration) pair, or run fresh otherwise.
 */
public class EvaluationCompareServlet extends HomeServlet {

  public static final String PARAM_MATCH_SET_ID = TestSetServlet.PARAM_MATCH_SET_ID;
  public static final String PARAM_CONFIGURATION_A_ID = "configurationAId";
  public static final String PARAM_CONFIGURATION_B_ID = "configurationBId";
  public static final String PARAM_FORCE_FRESH = "forceFresh";

  public static final String ACTION_COMPARE = "Compare";

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
      Configuration configurationA = null;
      if (req.getParameter(PARAM_CONFIGURATION_A_ID) != null) {
        configurationA = OrgScope.loadConfiguration(dataSession,
            Integer.parseInt(req.getParameter(PARAM_CONFIGURATION_A_ID)), user);
      }
      Configuration configurationB = null;
      if (req.getParameter(PARAM_CONFIGURATION_B_ID) != null) {
        configurationB = OrgScope.loadConfiguration(dataSession,
            Integer.parseInt(req.getParameter(PARAM_CONFIGURATION_B_ID)), user);
      }

      Evaluation evaluationA = null;
      Evaluation evaluationB = null;
      EvaluationComparison comparison = null;
      if (ACTION_COMPARE.equals(req.getParameter(PARAM_ACTION))) {
        if (matchSetSelected != null && configurationA != null && configurationB != null) {
          boolean forceFresh = "true".equals(req.getParameter(PARAM_FORCE_FRESH));
          boolean reusedA = false;
          boolean reusedB = false;
          if (!forceFresh) {
            evaluationA = OrgScope.findLatestEvaluation(dataSession, matchSetSelected, configurationA, user);
            reusedA = evaluationA != null;
          }
          if (evaluationA == null) {
            Transaction transaction = dataSession.beginTransaction();
            evaluationA = OrgScope.runEvaluation(dataSession, matchSetSelected, configurationA, user);
            transaction.commit();
          }
          if (!forceFresh) {
            evaluationB = OrgScope.findLatestEvaluation(dataSession, matchSetSelected, configurationB, user);
            reusedB = evaluationB != null;
          }
          if (evaluationB == null) {
            Transaction transaction = dataSession.beginTransaction();
            evaluationB = OrgScope.runEvaluation(dataSession, matchSetSelected, configurationB, user);
            transaction.commit();
          }
          comparison = OrgScope.compareEvaluations(dataSession, evaluationA, evaluationB);
          message = "Compared evaluation #" + evaluationA.getEvaluationId() + (reusedA ? " (reused)" : " (fresh)")
              + " vs. #" + evaluationB.getEvaluationId() + (reusedB ? " (reused)" : " (fresh)") + ".";
        } else {
          message = "Select a Test Set and both Configurations before comparing.";
        }
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("    <h1 class=\"aira-page-title\">Compare Configurations</h1>");

      printCompareForm(out, dataSession, user, matchSetSelected, configurationA, configurationB);

      if (comparison != null) {
        printComparison(out, evaluationA, evaluationB, comparison);
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

  private void printCompareForm(PrintWriter out, Session dataSession, User user, MatchSet matchSetSelected,
      Configuration configurationA, Configuration configurationB) {
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Select a Test Set and two Configurations</h2>");
    out.println("      <form action=\"EvaluationCompareServlet\" method=\"POST\">");
    out.println("        <table>");
    out.println("          <tr><td valign=\"top\">Test Set</td><td><select name=\"" + PARAM_MATCH_SET_ID
        + "\">");
    for (MatchSet matchSet : OrgScope.listMatchSets(dataSession, user)) {
      boolean selected = matchSetSelected != null && matchSetSelected.equals(matchSet);
      out.println("            <option value=\"" + matchSet.getMatchSetId() + "\""
          + (selected ? " selected=\"true\"" : "") + ">" + escapeHtml(matchSet.getLabel()) + "</option>");
    }
    out.println("          </select></td></tr>");
    List<Configuration> configurations = OrgScope.listConfigurations(dataSession, user);
    out.println("          <tr><td valign=\"top\">Configuration A</td><td><select name=\""
        + PARAM_CONFIGURATION_A_ID + "\">");
    printConfigurationOptions(out, configurations, configurationA);
    out.println("          </select></td></tr>");
    out.println("          <tr><td valign=\"top\">Configuration B</td><td><select name=\""
        + PARAM_CONFIGURATION_B_ID + "\">");
    printConfigurationOptions(out, configurations, configurationB);
    out.println("          </select></td></tr>");
    out.println("          <tr><td valign=\"top\">Force fresh run</td><td><input type=\"checkbox\" name=\""
        + PARAM_FORCE_FRESH + "\" value=\"true\"/> <span class=\"aira-muted\">(otherwise the most recent existing"
        + " evaluation for each Test Set + Configuration pair is reused if one exists)</span></td></tr>");
    out.println("          <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
        + "\" value=\"" + ACTION_COMPARE + "\"/></td></tr>");
    out.println("        </table>");
    out.println("      </form>");
    out.println("    </section>");
  }

  private void printConfigurationOptions(PrintWriter out, List<Configuration> configurations,
      Configuration selected) {
    for (Configuration configuration : configurations) {
      boolean isSelected = selected != null && selected.getConfigurationId() == configuration.getConfigurationId();
      out.println("            <option value=\"" + configuration.getConfigurationId() + "\""
          + (isSelected ? " selected=\"true\"" : "") + ">" + escapeHtml(configuration.getWorldName()) + " / "
          + escapeHtml(configuration.getIslandName()) + " (gen " + configuration.getGeneration() + ")</option>");
    }
  }

  private void printComparison(PrintWriter out, Evaluation evaluationA, Evaluation evaluationB,
      EvaluationComparison comparison) {
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Comparison Results</h2>");
    out.println("      <p>A = Evaluation #" + evaluationA.getEvaluationId() + " ("
        + escapeHtml(evaluationA.getConfiguration().getWorldName()) + " / "
        + escapeHtml(evaluationA.getConfiguration().getIslandName()) + ") &middot; B = Evaluation #"
        + evaluationB.getEvaluationId() + " (" + escapeHtml(evaluationB.getConfiguration().getWorldName()) + " / "
        + escapeHtml(evaluationB.getConfiguration().getIslandName()) + ")</p>");
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Improved</th><th>Regressed</th><th>Changed</th><th>Unchanged</th></tr>");
    out.println("        <tr><td>" + comparison.getImproved().size() + "</td><td>" + comparison.getRegressed().size()
        + "</td><td>" + comparison.getChanged().size() + "</td><td>" + comparison.getUnchanged().size()
        + "</td></tr>");
    out.println("      </table>");

    printCaseList(out, "Improved -- A disagreed, B agrees (the candidate configuration fixed this case)",
        comparison.getImproved(), evaluationA.getMatchSet().getMatchSetId());
    printCaseList(out, "Regressed -- A agreed, B disagrees (the candidate configuration broke this case)",
        comparison.getRegressed(), evaluationA.getMatchSet().getMatchSetId());
    printCaseList(out, "Changed -- both disagreed, but the calculated classification moved",
        comparison.getChanged(), evaluationA.getMatchSet().getMatchSetId());
    out.println("    </section>");
  }

  private void printCaseList(PrintWriter out, String title, List<EvaluationComparisonCase> cases, int matchSetId) {
    out.println("      <h3>" + escapeHtml(title) + " (" + cases.size() + ")</h3>");
    if (cases.isEmpty()) {
      out.println("      <p>None.</p>");
      return;
    }
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Test Case</th><th>Expected</th><th>A Calculated</th><th>B Calculated</th></tr>");
    for (EvaluationComparisonCase comparisonCase : cases) {
      String link = "TestSetServlet?" + TestSetServlet.PARAM_MATCH_SET_ID + "=" + matchSetId + "&"
          + TestSetServlet.PARAM_MATCH_ITEM_ID + "=" + comparisonCase.getResultA().getMatchItem().getMatchItemId();
      out.println("        <tr>");
      out.println("          <td><a href=\"" + link + "\">"
          + escapeHtml(comparisonCase.getResultA().getMatchItem().getLabel()) + "</a></td>");
      out.println("          <td>" + escapeHtml(comparisonCase.getResultA().getExpectedClassification())
          + "</td>");
      out.println("          <td>" + escapeHtml(comparisonCase.getResultA().getCalculatedClassification())
          + "</td>");
      out.println("          <td>" + escapeHtml(comparisonCase.getResultB().getCalculatedClassification())
          + "</td>");
      out.println("        </tr>");
    }
    out.println("      </table>");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doGet(req, resp);
  }
}
