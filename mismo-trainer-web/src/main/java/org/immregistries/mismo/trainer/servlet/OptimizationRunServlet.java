package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.optimizer.engine.FieldScope;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.OptimizationRun;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.TestSetSnapshot;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.runner.CandidateScoring;
import org.immregistries.mismo.trainer.runner.RunnerApi;
import org.immregistries.mismo.trainer.runner.RunnerScope;

/**
 * One optimization run: its frozen inputs, progress, candidates, and the selected candidate's 3 x 3
 * result table with its 2 x 2 potential-match collapse (docs/island-runner-implementation-plan.md
 * §6, Slice 3). Candidates link to the existing configuration pages. A running run can be marked
 * abandoned here, which changes only the record, not a local process.
 */
public class OptimizationRunServlet extends HomeServlet {

  public static final String PARAM_RUN_ID = "runId";
  public static final String PARAM_CANDIDATE_ID = "candidateId";
  public static final String ACTION_ABANDON = "Mark Run Abandoned";

  private static final String[] EXPECTED = { "Should Match", "Should be Possible", "Should Not Match" };
  private static final String[] ACTUAL = { "Matched", "Possible", "Not Matched" };

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
      OptimizationRun run = null;
      String message = null;
      try {
        run = RunnerScope.loadRun(dataSession, Integer.parseInt(req.getParameter(PARAM_RUN_ID)), user);
      } catch (NumberFormatException e) {
        // fall through to "not found"
      }
      if (run != null && ACTION_ABANDON.equals(req.getParameter(PARAM_ACTION))) {
        Transaction transaction = dataSession.beginTransaction();
        boolean abandoned = RunnerScope.abandonRun(run, user);
        dataSession.update(run);
        transaction.commit();
        message = abandoned ? "Run marked abandoned. If its runner is still going, it is told on its next report and"
            + " keeps optimizing locally until someone stops it." : "That run had already ended.";
      }

      HomeServlet.doHeader(out, req, user, run == null ? "Run not found." : message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      if (run != null) {
        printRun(out, dataSession, run, req.getParameter(PARAM_CANDIDATE_ID));
      } else {
        out.println("    <p><a href=\"RunnersServlet\">All runners</a></p>");
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

  private void printRun(PrintWriter out, Session dataSession, OptimizationRun run, String candidateParam) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    DecimalFormat rateFormat = new DecimalFormat("#,##0.0");
    boolean running = Protocol.RUN_RUNNING.equals(run.getStatus());
    ScoringProfile profile = run.getScoringProfile();
    TestSetSnapshot snapshot = run.getTestSetSnapshot();
    Configuration seed = run.getSeedConfiguration();

    out.println("    <p><a href=\"RunnersServlet\">All runners</a> &rsaquo; <a href=\"RunnersServlet?"
        + RunnersServlet.PARAM_RUNNER_ID + "=" + run.getRunner().getRunnerId() + "\">"
        + escapeHtml(run.getRunner().getDisplayName()) + "</a></p>");
    out.println("    <h1 class=\"aira-page-title\">Run " + run.getOptimizationRunId() + " &middot; "
        + escapeHtml(run.getStatus()) + "</h1>");

    if (running) {
      RunnerScope.State state = RunnerScope.state(run.getRunner(), run, System.currentTimeMillis());
      if (state == RunnerScope.State.OFFLINE || state == RunnerScope.State.REVOKED) {
        out.println("    <p><strong>The runner is " + state.label().toLowerCase() + "</strong> (last seen "
            + (run.getRunner().getLastSeenAt() == null ? "never" : sdf.format(run.getRunner().getLastSeenAt()))
            + "), but this run is still marked running. If the computer went away, mark the run abandoned below.</p>");
      }
    }

    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Inputs (frozen when the run started)</h2>");
    out.println("      <table>");
    row(out, "Assignment", escapeHtml(run.getRunnerAssignment().getName()) + ", revision "
        + run.getRunnerAssignment().getRevision());
    row(out, "Seed configuration", escapeHtml(RunnerApi.configurationLabel(seed)) + " (id " + seed.getConfigurationId()
        + ", SHA-256 " + shortHash(run.getSeedConfigurationSha256()) + ") " + configurationLinks(seed));
    row(out, "Test Set", escapeHtml(snapshot.getMatchSet().getLabel()) + ", snapshot " + snapshot.getTestSetSnapshotId()
        + ": " + String.format("%,d", snapshot.getCaseCount()) + " cases, " + escapeHtml(snapshot.getLifecycleStatus())
        + " when snapshotted (SHA-256 " + shortHash(snapshot.getContentSha256()) + ")"
        + (snapshot.getLifecycleStatus().equals("DRAFT") ? " <strong>Draft Test Set.</strong>" : ""));
    row(out, "Population", String.format("%,d", run.getPopulationSize()));
    row(out, "Scoring profile", escapeHtml(profile.getName()) + " (" + escapeHtml(profile.getPurpose()) + ", version "
        + profile.getVersion() + ") <a href=\"ScoringProfileServlet?" + ScoringProfileServlet.PARAM_SCORING_PROFILE_ID
        + "=" + profile.getScoringProfileId() + "\">View</a><pre>"
        + escapeHtml(ScoringWeights.format(ScoringWeights.fromJson(run.getScoringWeightsJson()))) + "</pre>");
    row(out, "Fields the seed uses", fieldScope(seed, profile));
    row(out, "Client", escapeHtml(run.getClientVersion()) + ", protocol " + escapeHtml(run.getProtocolVersion()));
    out.println("      </table>");
    out.println("    </section>");

    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Progress</h2>");
    out.println("      <table>");
    row(out, "Status", escapeHtml(run.getStatus())
        + (run.getStopReason() == null ? "" : " (" + escapeHtml(run.getStopReason()) + ")"));
    row(out, "Started", sdf.format(run.getStartedAt()));
    row(out, "Stopped", run.getStoppedAt() == null ? "&nbsp;" : sdf.format(run.getStoppedAt()));
    row(out, "Last report", run.getLastReportedAt() == null ? "&nbsp;" : sdf.format(run.getLastReportedAt()));
    row(out, "Generation", run.getLastGeneration() == null ? "&nbsp;" : String.format("%,d", run.getLastGeneration()));
    row(out, "Rate", run.getCandidateRate() == null ? "&nbsp;"
        : rateFormat.format(run.getCandidateRate()) + " candidates/second");
    row(out, "Best score", percent(run.getBestScore()) + " <small>under " + escapeHtml(profile.getName()) + " v"
        + profile.getVersion() + "</small>");
    out.println("      </table>");
    if (running) {
      out.println("      <form action=\"OptimizationRunServlet\" method=\"POST\">");
      out.println("        <input type=\"hidden\" name=\"" + PARAM_RUN_ID + "\" value=\"" + run.getOptimizationRunId()
          + "\"/>");
      out.println("        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_ABANDON + "\"/>");
      out.println("        <small>Only changes this record. It does not stop a runner that is still going.</small>");
      out.println("      </form>");
    }
    out.println("    </section>");

    List<Configuration> candidates = RunnerScope.listCandidates(dataSession, run);
    Configuration selected = run.getBestConfiguration();
    if (candidateParam != null) {
      for (Configuration candidate : candidates) {
        if (String.valueOf(candidate.getConfigurationId()).equals(candidateParam)) {
          selected = candidate;
        }
      }
    }
    if (selected != null) {
      printResults(out, run, selected);
    }
    printCandidates(out, run, candidates, selected, sdf);
  }

  private void printResults(PrintWriter out, OptimizationRun run, Configuration candidate) {
    CandidateScoring.Result result = CandidateScoring.score(run, candidate);
    int[][] counts = result.counts();
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Results for candidate " + candidate.getConfigurationId()
        + " (generation " + candidate.getGeneration() + ")"
        + (run.getBestConfiguration() != null
            && run.getBestConfiguration().getConfigurationId() == candidate.getConfigurationId() ? ", the best" : "")
        + "</h2>");
    out.println("      <p>Scored against this run's own Test Set snapshot and weights: <strong>"
        + percent(result.score()) + "</strong>.</p>");
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>&nbsp;</th><th>" + ACTUAL[0] + "</th><th>" + ACTUAL[1] + "</th><th>" + ACTUAL[2]
        + "</th></tr>");
    for (int i = 0; i < 3; i++) {
      out.println("        <tr><th align=\"left\">" + EXPECTED[i] + "</th><td>" + counts[i][0] + "</td><td>"
          + counts[i][1] + "</td><td>" + counts[i][2] + "</td></tr>");
    }
    out.println("      </table>");

    int[][] potential = result.potentialMatchTable();
    out.println("      <h3>Measurement view: potential match</h3>");
    out.println("      <p>Match and Possible Match counted as one signal, as the national measurement program uses it"
        + " (docs/optimization-objectives-and-scoring-profiles.md).</p>");
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>&nbsp;</th><th>Flagged as potential match</th><th>Not flagged</th><th>Flagged share</th></tr>");
    String[] rows = { "Should be a potential match", "Should not" };
    for (int i = 0; i < 2; i++) {
      int total = potential[i][0] + potential[i][1];
      out.println("        <tr><th align=\"left\">" + rows[i] + "</th><td>" + potential[i][0] + "</td><td>"
          + potential[i][1] + "</td><td>" + (total == 0 ? "&nbsp;" : percent((double) potential[i][0] / total))
          + "</td></tr>");
    }
    out.println("      </table>");
    out.println("    </section>");
  }

  private void printCandidates(PrintWriter out, OptimizationRun run, List<Configuration> candidates,
      Configuration selected, SimpleDateFormat sdf) {
    out.println("    <h2>Candidates</h2>");
    if (candidates.isEmpty()) {
      out.println("    <p>None uploaded yet.</p>");
      return;
    }
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Candidate</th><th>Generation</th><th>Score</th><th>Uploaded</th><th>Signature hash</th>"
        + "<th>&nbsp;</th></tr>");
    for (Configuration candidate : candidates) {
      boolean best = run.getBestConfiguration() != null
          && run.getBestConfiguration().getConfigurationId() == candidate.getConfigurationId();
      boolean isSelected = selected != null && selected.getConfigurationId() == candidate.getConfigurationId();
      out.println("      <tr" + (best ? " class=\"pass\"" : "") + ">");
      out.println("        <td>" + candidate.getConfigurationId() + (best ? " (best)" : "") + "</td>");
      out.println("        <td>" + String.format("%,d", candidate.getGeneration()) + "</td>");
      out.println("        <td>" + percent(candidate.getGenerationScore()) + "</td>");
      out.println("        <td>" + sdf.format(candidate.getCreatedAt()) + "</td>");
      out.println("        <td><code>" + escapeHtml(candidate.getHashForSignature()) + "</code></td>");
      out.println("        <td>" + (isSelected ? "Shown above"
          : "<a href=\"OptimizationRunServlet?" + PARAM_RUN_ID + "=" + run.getOptimizationRunId() + "&"
              + PARAM_CANDIDATE_ID + "=" + candidate.getConfigurationId() + "\">Result tables</a>")
          + " " + configurationLinks(candidate) + "</td>");
      out.println("      </tr>");
    }
    out.println("    </table>");
  }

  /** Links to the existing configuration pages; {@code configurationId} also loads it for the session. */
  private static String configurationLinks(Configuration configuration) {
    return "<a href=\"EvaluationServlet?" + PARAM_CONFIGURATION_ID + "=" + configuration.getConfigurationId()
        + "\">Evaluate</a> <a href=\"WeightSetServlet?" + PARAM_CONFIGURATION_ID + "="
        + configuration.getConfigurationId() + "\">Select</a>";
  }

  /** Field list, with the D6 item 9 warning for a measurement profile on a wider seed. */
  static String fieldScope(Configuration seed, ScoringProfile profile) {
    String script = seed.getConfigurationScript();
    Set<String> fields = FieldScope.of(script);
    Set<String> outside = FieldScope.outsideMeasurementCore(script);
    String text = escapeHtml(String.join(", ", fields));
    if (RunnerScope.PURPOSE_MEASUREMENT.equals(profile.getPurpose()) && !outside.isEmpty()) {
      text += "<br/><strong>Warning: the seed uses fields outside the measurement core ("
          + escapeHtml(String.join(", ", outside)) + ") under a measurement profile.</strong>";
    }
    return text;
  }

  private static void row(PrintWriter out, String label, String html) {
    out.println("        <tr><th align=\"left\" valign=\"top\">" + label + "</th><td>" + html + "</td></tr>");
  }

  private static String shortHash(String sha256) {
    return "<code>" + sha256.substring(0, 12) + "</code>";
  }

  private static String percent(Double score) {
    return score == null ? "&nbsp;" : new DecimalFormat("#0.00").format(score * 100.0) + "%";
  }
}
