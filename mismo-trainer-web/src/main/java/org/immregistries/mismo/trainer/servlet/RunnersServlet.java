package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.auth.HubClientSupport;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.OptimizationRun;
import org.immregistries.mismo.trainer.model.Runner;
import org.immregistries.mismo.trainer.model.RunnerAssignment;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.runner.RunnerApi;
import org.immregistries.mismo.trainer.runner.RunnerScope;

/**
 * Runners: create and pair local Mismo Runners, set the assignment for each one's next run, and
 * list its runs with their scores (docs/island-runner-implementation-plan.md §6, Slice 1). List
 * view without {@code runnerId}, detail view with it. The pairing code is shown only in the
 * response that created it; only its hash is stored.
 *
 * <p>The seed configuration parameter is deliberately not {@code configurationId}:
 * {@code HomeServlet.setup} would treat that as "load this configuration into my session".
 */
public class RunnersServlet extends HomeServlet {

  public static final String PARAM_RUNNER_ID = "runnerId";
  public static final String PARAM_DISPLAY_NAME = "displayName";
  public static final String PARAM_ASSIGNMENT_NAME = "assignmentName";
  public static final String PARAM_SEED_CONFIGURATION_ID = "seedConfigurationId";
  public static final String PARAM_MATCH_SET_ID = TestSetServlet.PARAM_MATCH_SET_ID;
  public static final String PARAM_POPULATION_SIZE = "populationSize";
  public static final String PARAM_SCORING_PROFILE_ID = "scoringProfileId";

  public static final String ACTION_CREATE_RUNNER = "Create Runner";
  public static final String ACTION_NEW_PAIRING_CODE = "New Pairing Code";
  public static final String ACTION_SAVE_ASSIGNMENT = "Save Assignment";

  private static final int DEFAULT_POPULATION_SIZE = 1000;
  private static final int MIN_POPULATION_SIZE = 10;
  private static final int MAX_POPULATION_SIZE = 100000;

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
      String pairingCode = null;

      Runner runner = null;
      if (req.getParameter(PARAM_RUNNER_ID) != null) {
        runner = RunnerScope.loadRunner(dataSession, Integer.parseInt(req.getParameter(PARAM_RUNNER_ID)), user);
        if (runner == null) {
          message = "Runner not found.";
        }
      }

      String action = req.getParameter(PARAM_ACTION);
      if (ACTION_CREATE_RUNNER.equals(action)) {
        String displayName = trim(req.getParameter(PARAM_DISPLAY_NAME));
        if (displayName.isEmpty() || displayName.length() > 100) {
          message = "Give the runner a name of up to 100 characters, such as the computer it runs on.";
        } else {
          Transaction transaction = dataSession.beginTransaction();
          RunnerScope.CreatedRunner created = RunnerScope.createRunner(dataSession, displayName, user);
          transaction.commit();
          runner = created.runner();
          pairingCode = created.pairingCode();
          message = "Runner \"" + displayName + "\" created.";
        }
      } else if (ACTION_NEW_PAIRING_CODE.equals(action) && runner != null) {
        Transaction transaction = dataSession.beginTransaction();
        pairingCode = RunnerScope.setNewPairingCode(runner);
        dataSession.update(runner);
        transaction.commit();
        message = "New pairing code created. The runner's current credential keeps working until it pairs again.";
      } else if (ACTION_SAVE_ASSIGNMENT.equals(action) && runner != null) {
        message = saveAssignment(req, dataSession, runner, user);
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      if (runner == null) {
        printRunnerList(out, dataSession, user);
      } else {
        printRunner(out, dataSession, user, runner, pairingCode);
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

  private static String saveAssignment(HttpServletRequest req, Session dataSession, Runner runner, User user) {
    String name = trim(req.getParameter(PARAM_ASSIGNMENT_NAME));
    Configuration configuration = OrgScope.loadConfiguration(dataSession,
        parseInt(req.getParameter(PARAM_SEED_CONFIGURATION_ID), -1), user);
    MatchSet matchSet = OrgScope.loadMatchSet(dataSession, parseInt(req.getParameter(PARAM_MATCH_SET_ID), -1), user);
    ScoringProfile profile = RunnerScope.loadScoringProfile(dataSession,
        parseInt(req.getParameter(PARAM_SCORING_PROFILE_ID), -1), user.getOrganization());
    int populationSize = parseInt(req.getParameter(PARAM_POPULATION_SIZE), -1);
    if (name.isEmpty() || name.length() > 150) {
      return "Give the assignment a name of up to 150 characters.";
    }
    if (configuration == null || matchSet == null || profile == null) {
      return "Choose a seed configuration, a Test Set and a scoring profile.";
    }
    if (populationSize < MIN_POPULATION_SIZE || populationSize > MAX_POPULATION_SIZE) {
      return "Population size must be between " + MIN_POPULATION_SIZE + " and " + MAX_POPULATION_SIZE + ".";
    }
    Transaction transaction = dataSession.beginTransaction();
    RunnerAssignment assignment = RunnerScope.saveAssignment(dataSession, runner, name, configuration, matchSet,
        populationSize, profile, user);
    transaction.commit();
    OptimizationRun active = RunnerScope.activeRun(dataSession, runner);
    return "Saved assignment revision " + assignment.getRevision() + "."
        + (active == null ? "" : " Run " + active.getOptimizationRunId() + " keeps its own inputs; the runner uses"
            + " this revision the next time it starts a run.");
  }

  private void printRunnerList(PrintWriter out, Session dataSession, User user) {
    out.println("    <h1 class=\"aira-page-title\">Runners</h1>");
    out.println("    <p>A Runner is one installed copy of the Mismo Runner on one computer. It runs optimization"
        + " locally, using the assignment set here, and reports progress and candidates back.</p>");
    List<Runner> runners = RunnerScope.listRunners(dataSession, user);
    Map<Integer, OptimizationRun> latestRuns = RunnerScope.latestRuns(dataSession, runners);
    if (runners.isEmpty()) {
      out.println("    <p>No runners yet.</p>");
    } else {
      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr><th>Runner</th><th>State</th><th>Active/last run</th><th>Generation</th>"
          + "<th>Best score</th><th>Profile</th><th>Last seen</th></tr>");
      for (Runner runner : runners) {
        OptimizationRun run = latestRuns.get(runner.getRunnerId());
        out.println("      <tr>");
        out.println("        <td><a href=\"RunnersServlet?" + PARAM_RUNNER_ID + "=" + runner.getRunnerId() + "\">"
            + escapeHtml(runner.getDisplayName()) + "</a></td>");
        out.println("        <td>" + state(runner, run) + "</td>");
        out.println("        <td>" + (run == null ? "&nbsp;" : "Run " + run.getOptimizationRunId() + " ("
            + escapeHtml(run.getStatus()) + ")") + "</td>");
        out.println("        <td>" + (run == null || run.getLastGeneration() == null ? "&nbsp;"
            : String.format("%,d", run.getLastGeneration())) + "</td>");
        out.println("        <td>" + (run == null ? "&nbsp;" : percent(run.getBestScore())) + "</td>");
        out.println("        <td>" + (run == null ? "&nbsp;" : escapeHtml(run.getScoringProfile().getName())) + "</td>");
        out.println("        <td>" + (runner.getLastSeenAt() == null ? "never" : sdf.format(runner.getLastSeenAt()))
            + "</td>");
        out.println("      </tr>");
      }
      out.println("    </table>");
    }
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Create Runner</h2>");
    out.println("      <form action=\"RunnersServlet\" method=\"POST\">");
    out.println("        <label for=\"" + PARAM_DISPLAY_NAME + "\">Name</label>");
    out.println("        <input type=\"text\" size=\"40\" maxlength=\"100\" name=\"" + PARAM_DISPLAY_NAME + "\" id=\""
        + PARAM_DISPLAY_NAME + "\" placeholder=\"e.g. Office Optimization Workstation\"/>");
    out.println("        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_CREATE_RUNNER + "\"/>");
    out.println("      </form>");
    out.println("    </section>");
  }

  private void printRunner(PrintWriter out, Session dataSession, User user, Runner runner, String pairingCode) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    String runnerParam = PARAM_RUNNER_ID + "=" + runner.getRunnerId();
    out.println("    <p><a href=\"RunnersServlet\">All runners</a></p>");
    out.println("    <h1 class=\"aira-page-title\">" + escapeHtml(runner.getDisplayName()) + "</h1>");

    if (pairingCode != null) {
      String serverUrl = HubClientSupport.getAbsoluteMismoUrl("/");
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
      out.println("    <section class=\"aira-panel\">");
      out.println("      <h2 class=\"aira-panel__title\">Pairing code</h2>");
      out.println("      <p style=\"font-size: 2em; font-family: monospace;\">" + escapeHtml(pairingCode) + "</p>");
      out.println("      <p>Valid until " + sdf.format(runner.getPairingExpiresAt()) + ". It is shown only now;"
          + " if it expires, create a new one below.</p>");
      out.println("      <ol>");
      out.println("        <li>On the computer that will run optimization, start the runner:"
          + " <code>java -jar mismo-runner.jar</code> (Java 17 or later).</li>");
      out.println("        <li>Type <code>pair</code>.</li>");
      out.println("        <li>Enter the server address <code>" + escapeHtml(serverUrl) + "</code> and this code.</li>");
      out.println("      </ol>");
      out.println("    </section>");
    }

    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Installation</h2>");
    out.println("      <table>");
    out.println("        <tr><th align=\"left\">Paired</th><td>" + (runner.getCredentialHash() == null ? "Not yet"
        : "Yes, " + sdf.format(runner.getCredentialCreatedAt())) + "</td></tr>");
    out.println("        <tr><th align=\"left\">Last seen</th><td>"
        + (runner.getLastSeenAt() == null ? "never" : sdf.format(runner.getLastSeenAt())) + "</td></tr>");
    out.println("        <tr><th align=\"left\">Client version</th><td>" + escapeHtml(runner.getClientVersion())
        + "</td></tr>");
    out.println("        <tr><th align=\"left\">Protocol</th><td>" + escapeHtml(runner.getProtocolVersion())
        + "</td></tr>");
    out.println("        <tr><th align=\"left\">Runtime</th><td>" + escapeHtml(runner.getRuntimeInfo()) + "</td></tr>");
    out.println("      </table>");
    out.println("      <form action=\"RunnersServlet\" method=\"POST\">");
    out.println("        <input type=\"hidden\" name=\"" + PARAM_RUNNER_ID + "\" value=\"" + runner.getRunnerId() + "\"/>");
    out.println("        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_NEW_PAIRING_CODE
        + "\"/>");
    out.println("      </form>");
    out.println("    </section>");

    RunnerAssignment current = RunnerScope.currentAssignment(dataSession, runner);
    OptimizationRun active = RunnerScope.activeRun(dataSession, runner);
    printAssignment(out, dataSession, user, runner, current, active);
    printRuns(out, dataSession, runner);
  }

  private void printAssignment(PrintWriter out, Session dataSession, User user, Runner runner,
      RunnerAssignment current, OptimizationRun active) {
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Assignment for the next run</h2>");
    if (current == null) {
      out.println("      <p>None yet. The runner can't start a run until it has one.</p>");
    } else {
      if (active != null
          && active.getRunnerAssignment().getRunnerAssignmentId() != current.getRunnerAssignmentId()) {
        out.println("      <p><strong>Assignment changes are ready and will be used the next time this runner starts"
            + " a run.</strong> Run " + active.getOptimizationRunId() + " is still using revision "
            + active.getRunnerAssignment().getRevision() + ".</p>");
      }
      MatchSet matchSet = current.getMatchSet();
      ScoringProfile profile = current.getScoringProfile();
      out.println("      <table>");
      out.println("        <tr><th align=\"left\">Revision</th><td>" + current.getRevision() + " (saved "
          + new SimpleDateFormat("MM/dd/yyyy HH:mm").format(current.getCreatedAt()) + ")</td></tr>");
      out.println("        <tr><th align=\"left\">Name</th><td>" + escapeHtml(current.getName()) + "</td></tr>");
      out.println("        <tr><th align=\"left\">Seed configuration</th><td>"
          + escapeHtml(RunnerApi.configurationLabel(current.getConfiguration())) + "</td></tr>");
      out.println("        <tr><th align=\"left\">Test Set</th><td>" + escapeHtml(matchSet.getLabel()) + " ("
          + escapeHtml(matchSet.getLifecycleStatus()) + ")"
          + (MatchSet.LIFECYCLE_DRAFT.equals(matchSet.getLifecycleStatus())
              ? " <strong>Warning: this Test Set is still a draft.</strong>" : "")
          + "<br/><small>Snapshotted when the runner starts, so later edits don't affect that run.</small></td></tr>");
      out.println("        <tr><th align=\"left\">Population</th><td>" + String.format("%,d", current.getPopulationSize())
          + "</td></tr>");
      out.println("        <tr><th align=\"left\">Scoring profile</th><td>" + escapeHtml(profile.getName()) + " ("
          + escapeHtml(profile.getPurpose()) + ", version " + profile.getVersion() + ")<pre>"
          + escapeHtml(ScoringWeights.format(ScoringWeights.fromJson(profile.getScoringWeightsJson())))
          + "</pre></td></tr>");
      int[][] seedWeights = ScoringWeights.readFrom(current.getConfiguration().getConfigurationScript());
      if (!ScoringWeights.same(seedWeights, ScoringWeights.fromJson(profile.getScoringWeightsJson()))) {
        out.println("        <tr><th></th><td><strong>The seed configuration was trained under different weights."
            + "</strong> The runner will re-steer it with this profile.</td></tr>");
      }
      out.println("      </table>");
    }

    ScoringProfile selectedProfile = current == null
        ? RunnerScope.defaultScoringProfile(dataSession, user.getOrganization())
        : current.getScoringProfile();
    out.println("      <h3>" + (current == null ? "Set the assignment" : "Change the assignment") + "</h3>");
    out.println("      <form action=\"RunnersServlet\" method=\"POST\">");
    out.println("        <input type=\"hidden\" name=\"" + PARAM_RUNNER_ID + "\" value=\"" + runner.getRunnerId() + "\"/>");
    out.println("        <table>");
    out.println("          <tr><td>Name</td><td><input type=\"text\" size=\"50\" maxlength=\"150\" name=\""
        + PARAM_ASSIGNMENT_NAME + "\" value=\"" + escapeHtml(current == null ? "" : current.getName()) + "\"/></td></tr>");
    out.println("          <tr><td>Seed configuration</td><td><select name=\"" + PARAM_SEED_CONFIGURATION_ID + "\">");
    for (Configuration configuration : OrgScope.listConfigurations(dataSession, user)) {
      if (configuration.getOptimizationRun() != null && (current == null
          || current.getConfiguration().getConfigurationId() != configuration.getConfigurationId())) {
        continue; // run candidates are listed on their run, not as seeds
      }
      boolean selected = current != null
          && current.getConfiguration().getConfigurationId() == configuration.getConfigurationId();
      out.println("            <option value=\"" + configuration.getConfigurationId() + "\""
          + (selected ? " selected=\"true\"" : "") + ">" + escapeHtml(RunnerApi.configurationLabel(configuration))
          + "</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td>Test Set</td><td><select name=\"" + PARAM_MATCH_SET_ID + "\">");
    for (MatchSet matchSet : OrgScope.listMatchSets(dataSession, user)) {
      boolean selected = current != null && current.getMatchSet().getMatchSetId() == matchSet.getMatchSetId();
      out.println("            <option value=\"" + matchSet.getMatchSetId() + "\"" + (selected ? " selected=\"true\"" : "")
          + ">" + escapeHtml(matchSet.getLabel()) + " (" + escapeHtml(matchSet.getLifecycleStatus()) + ")</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td>Population size</td><td><input type=\"number\" min=\"" + MIN_POPULATION_SIZE
        + "\" max=\"" + MAX_POPULATION_SIZE + "\" name=\"" + PARAM_POPULATION_SIZE + "\" value=\""
        + (current == null ? DEFAULT_POPULATION_SIZE : current.getPopulationSize()) + "\"/></td></tr>");
    out.println("          <tr><td>Scoring profile</td><td><select name=\"" + PARAM_SCORING_PROFILE_ID + "\">");
    for (ScoringProfile profile : RunnerScope.listScoringProfiles(dataSession, user.getOrganization())) {
      boolean selected = selectedProfile != null
          && selectedProfile.getScoringProfileId() == profile.getScoringProfileId();
      out.println("            <option value=\"" + profile.getScoringProfileId() + "\"" + (selected ? " selected=\"true\"" : "")
          + ">" + escapeHtml(profile.getName()) + " (" + escapeHtml(profile.getPurpose()) + ", v" + profile.getVersion()
          + ")</option>");
    }
    out.println("          </select> <small>See docs/optimization-objectives-and-scoring-profiles.md.</small></td></tr>");
    out.println("          <tr><td></td><td><input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\""
        + ACTION_SAVE_ASSIGNMENT + "\"/>" + (active == null ? ""
            : " <small>A run is active; it keeps its inputs. This applies to the next run.</small>") + "</td></tr>");
    out.println("        </table>");
    out.println("      </form>");
    out.println("    </section>");
  }

  private void printRuns(PrintWriter out, Session dataSession, Runner runner) {
    List<OptimizationRun> runs = RunnerScope.listRuns(dataSession, runner);
    out.println("    <h2>Runs</h2>");
    if (runs.isEmpty()) {
      out.println("    <p>No runs yet. Type <code>start</code> in the runner to start one.</p>");
      return;
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    DecimalFormat rateFormat = new DecimalFormat("#,##0.0");
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Run</th><th>Status</th><th>Revision</th><th>Test Set snapshot</th><th>Profile</th>"
        + "<th>Started</th><th>Last report</th><th>Generation</th><th>Rate</th><th>Best score</th>"
        + "<th>Candidates</th><th>Best candidate</th></tr>");
    for (OptimizationRun run : runs) {
      List<Configuration> candidates = RunnerScope.listCandidates(dataSession, run);
      out.println("      <tr" + (Protocol.RUN_RUNNING.equals(run.getStatus()) ? " class=\"pass\"" : "") + ">");
      out.println("        <td>" + run.getOptimizationRunId() + "</td>");
      out.println("        <td>" + escapeHtml(run.getStatus())
          + (run.getStopReason() == null ? "" : "<br/><small>" + escapeHtml(run.getStopReason()) + "</small>") + "</td>");
      out.println("        <td>" + run.getRunnerAssignment().getRevision() + "</td>");
      out.println("        <td>" + escapeHtml(run.getTestSetSnapshot().getMatchSet().getLabel()) + " #"
          + run.getTestSetSnapshot().getTestSetSnapshotId() + ", " + run.getTestSetSnapshot().getCaseCount()
          + " cases</td>");
      out.println("        <td>" + escapeHtml(run.getScoringProfile().getName()) + " v"
          + run.getScoringProfile().getVersion() + "</td>");
      out.println("        <td>" + sdf.format(run.getStartedAt()) + "</td>");
      out.println("        <td>" + (run.getLastReportedAt() == null ? "&nbsp;" : sdf.format(run.getLastReportedAt()))
          + "</td>");
      out.println("        <td>" + (run.getLastGeneration() == null ? "&nbsp;"
          : String.format("%,d", run.getLastGeneration())) + "</td>");
      out.println("        <td>" + (run.getCandidateRate() == null ? "&nbsp;"
          : rateFormat.format(run.getCandidateRate()) + "/s") + "</td>");
      out.println("        <td>" + percent(run.getBestScore()) + "</td>");
      out.println("        <td>" + candidates.size() + "</td>");
      Configuration best = run.getBestConfiguration();
      out.println("        <td>" + (best == null ? "&nbsp;"
          : "gen " + best.getGeneration() + " <a href=\"EvaluationServlet?" + PARAM_CONFIGURATION_ID + "="
              + best.getConfigurationId() + "\">Evaluate</a> <a href=\"WeightSetServlet?" + PARAM_CONFIGURATION_ID
              + "=" + best.getConfigurationId() + "\">Select</a>") + "</td>");
      out.println("      </tr>");
    }
    out.println("    </table>");
  }

  private static String state(Runner runner, OptimizationRun latest) {
    if (runner.getCredentialHash() == null) {
      return "Not paired";
    }
    if (latest != null && Protocol.RUN_RUNNING.equals(latest.getStatus())) {
      return "Running";
    }
    return "Paired";
  }

  private static String percent(Double score) {
    return score == null ? "&nbsp;" : new DecimalFormat("#0.00").format(score * 100.0) + "%";
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private static int parseInt(String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
