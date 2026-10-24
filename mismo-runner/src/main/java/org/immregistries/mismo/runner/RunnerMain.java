package org.immregistries.mismo.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.TestCaseLoader;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;

/**
 * The Mismo Runner console application (docs/centrally-managed-local-island-runner.md §6–§7):
 * pair once, then {@code start} fetches the current assignment from Mismo Trainer, verifies the
 * inputs, and runs optimization locally until {@code stop} or {@code exit}. Nothing about a run is
 * configured locally.
 */
public class RunnerMain {

  static final String VERSION = version();

  private static final String IDLE_HELP = String.join("\n",
      "  start       Fetch the current assignment and start a new run",
      "  assignment  Show the assignment that would be used for the next run",
      "  status      Show runner identity and connection status",
      "  pair        Pair or re-pair this installation",
      "  help        Show available commands",
      "  exit        Exit the application");

  private static final String RUNNING_HELP = String.join("\n",
      "  status      Redraw the optimization dashboard (or just press Enter)",
      "  sync        Upload current progress and best candidate now",
      "  weights     Display scoring weights for this run",
      "  script      Display the current best configuration",
      "  stop        Stop this run and return to the idle prompt",
      "  exit        Stop this run cleanly and exit",
      "  help        Show available commands");

  static final long HEARTBEAT_MINUTES = 5;
  /** How long Ctrl+C waits for the final upload and stop before the process ends. */
  private static final long SHUTDOWN_LIMIT_MILLIS = 5_000L;

  private final BufferedReader in;
  private final PrintStream out;
  private final Path home;
  private final IdentityStore identityStore;
  private final InputCache inputCache;
  private final Checkpoints checkpoints;
  private volatile IdentityStore.Identity identity;
  private volatile RunSession activeSession;

  RunnerMain(BufferedReader in, PrintStream out, Path home) {
    this.in = in;
    this.out = out;
    this.home = home;
    this.identityStore = new IdentityStore(home);
    this.inputCache = new InputCache(home);
    this.checkpoints = new Checkpoints(home);
  }

  public static void main(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    RunnerMain runner = new RunnerMain(in, System.out, RunnerHome.directory());
    Runtime.getRuntime().addShutdownHook(new Thread(runner::onShutdown, "mismo-shutdown"));
    runner.run();
  }

  /**
   * Ctrl+C or the console window closing: a best-effort final upload and stop, limited to a few
   * seconds. Whatever doesn't make it is covered by the checkpoint.
   */
  private void onShutdown() {
    RunSession session = activeSession;
    if (session == null || session.isEnded()) {
      return;
    }
    Thread stopper = new Thread(() -> {
      String outcome = session.stop("interrupted (Ctrl+C or window closed)", 2_000L);
      RunnerLog.LOG.info(outcome);
      out.println(outcome);
    }, "mismo-shutdown-stop");
    stopper.start();
    try {
      stopper.join(SHUTDOWN_LIMIT_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String version() {
    String version = RunnerMain.class.getPackage().getImplementationVersion();
    return version == null ? "dev" : version;
  }

  void run() throws IOException {
    RunnerLog.setUp(home);
    RunnerLog.LOG.info("Mismo Runner " + VERSION + " starting; " + runtimeInfo());
    out.println("Mismo Runner " + VERSION);
    try {
      identity = identityStore.load();
    } catch (IOException | RuntimeException e) {
      out.println("Could not read " + identityStore.path() + " (" + e.getMessage() + "). Pair this runner again.");
    }
    if (identity == null) {
      out.println("This installation is not paired yet. Type 'pair' to connect it to Mismo Trainer.");
    } else {
      out.println("Paired as " + identity.runnerName() + " (" + identity.organizationName() + ") with "
          + identity.serverUrl());
      recoverCheckpoints();
    }
    out.println("Type 'help' for commands.");
    ScheduledExecutorService heartbeat = startHeartbeat();
    try {
      idleLoop();
    } finally {
      heartbeat.shutdownNow();
      RunnerLog.LOG.info("Mismo Runner exiting");
    }
  }

  /** While idle at the prompt, tells Mismo Trainer every few minutes that this runner is still here. */
  private ScheduledExecutorService startHeartbeat() {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mismo-heartbeat");
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleWithFixedDelay(() -> {
      if (identity != null && activeSession == null) {
        try {
          api().heartbeat();
        } catch (ApiClient.ApiError e) {
          RunnerLog.LOG.info("Heartbeat failed: " + e.getMessage());
        }
      }
    }, HEARTBEAT_MINUTES, HEARTBEAT_MINUTES, TimeUnit.MINUTES);
    return scheduler;
  }

  /**
   * Runs that this runner left RUNNING (it crashed or was killed) still have a checkpoint. Offers
   * to upload each one's best candidate and mark it FAILED.
   */
  private void recoverCheckpoints() throws IOException {
    List<Checkpoints.Checkpoint> found;
    try {
      found = checkpoints.list(identity.serverUrl(), identity.runnerId());
    } catch (IOException e) {
      RunnerLog.LOG.warning("Could not read checkpoints: " + e);
      return;
    }
    ApiClient api = api();
    for (Checkpoints.Checkpoint checkpoint : found) {
      Messages.RunInfo info;
      try {
        info = api.run(checkpoint.runId());
      } catch (ApiClient.ApiError e) {
        if ("NOT_FOUND".equals(e.code())) {
          checkpoints.delete(checkpoint.runId());
        } else {
          out.println("Could not check run " + checkpoint.runId() + " (" + e.getMessage() + "). Its checkpoint is kept.");
        }
        continue;
      }
      if (!Protocol.RUN_RUNNING.equals(info.status())) {
        out.println("Run " + checkpoint.runId() + " already ended in Mismo Trainer (" + info.status()
            + "); removing its local checkpoint.");
        checkpoints.delete(checkpoint.runId());
        continue;
      }
      out.println();
      out.println("Run " + checkpoint.runId() + " (" + checkpoint.assignmentName() + ", revision "
          + checkpoint.revision() + ") did not stop cleanly.");
      out.println("Its best candidate scored " + String.format("%.2f%%", checkpoint.bestScore() * 100.0)
          + " at generation " + checkpoint.bestGeneration() + " (saved " + Dashboard.ago(checkpoint.savedAtMillis())
          + ").");
      String answer = prompt("Upload that candidate and mark the run FAILED? [Y/n] ");
      if (answer == null || !(answer.isEmpty() || answer.toLowerCase(Locale.ROOT).startsWith("y"))) {
        out.println("Left as it is. The run will be marked ABANDONED when this runner starts its next run.");
        continue;
      }
      try {
        api.candidate(checkpoint.runId(), new Messages.CandidateUpload(checkpoint.bestGeneration(),
            checkpoint.bestScore(), checkpoint.bestScript()));
        api.fail(checkpoint.runId(), "runner restarted");
        checkpoints.delete(checkpoint.runId());
        RunnerLog.LOG.info("Recovered run " + checkpoint.runId() + " from its checkpoint");
        out.println("Uploaded. Run " + checkpoint.runId() + " is marked FAILED (runner restarted).");
      } catch (ApiClient.ApiError e) {
        out.println("Could not recover run " + checkpoint.runId() + ": " + e.getMessage()
            + " Its checkpoint is kept for next time.");
      }
    }
  }

  private void idleLoop() throws IOException {
    while (true) {
      String command = prompt("mismo> ");
      if (command == null) {
        return;
      }
      switch (command.toLowerCase(Locale.ROOT)) {
      case "":
        break;
      case "start":
        RunSession session = startRun();
        if (session != null && runLoop(session)) {
          return;
        }
        break;
      case "assignment":
        showAssignment();
        break;
      case "status":
        showStatus();
        break;
      case "pair":
        pair();
        break;
      case "help":
      case "?":
        out.println(IDLE_HELP);
        break;
      case "exit":
      case "quit":
        return;
      default:
        out.println("Unknown command '" + command + "'. Type 'help' for commands.");
      }
    }
  }

  private String prompt(String text) throws IOException {
    out.print(text);
    out.flush();
    String line = in.readLine();
    return line == null ? null : line.trim();
  }

  // --- idle commands ------------------------------------------------------------------------

  private void pair() throws IOException {
    String defaultUrl = identity == null ? "" : identity.serverUrl();
    String url = prompt("Mismo Trainer address" + (defaultUrl.isEmpty() ? "" : " [" + defaultUrl + "]") + ": ");
    if (url == null) {
      return;
    }
    if (url.isEmpty()) {
      url = defaultUrl;
    }
    String problem = ApiClient.checkServerUrl(url);
    if (problem != null) {
      out.println(problem);
      return;
    }
    String code = prompt("Pairing code: ");
    if (code == null || code.isEmpty()) {
      return;
    }
    ApiClient api = new ApiClient(url, null);
    try {
      Messages.PairingResponse response = api.pair(new Messages.PairingRequest(code, VERSION,
          Protocol.PROTOCOL_VERSION, runtimeInfo()));
      identity = new IdentityStore.Identity(api.serverUrl(), response.runnerId(), response.runnerName(),
          response.organizationName(), response.credential());
      identityStore.save(identity);
      RunnerLog.LOG.info("Paired as runner " + response.runnerId() + " (" + response.runnerName() + ") with "
          + api.serverUrl());
      out.println("Paired as " + response.runnerName() + " (" + response.organizationName() + ").");
      out.println("Set this runner's assignment in Mismo Trainer, then type 'start'.");
    } catch (ApiClient.ApiError e) {
      out.println("Pairing failed: " + e.getMessage());
    }
  }

  private void showStatus() {
    if (!requirePaired()) {
      return;
    }
    out.println("Server:       " + identity.serverUrl());
    out.println("Runner:       " + identity.runnerName() + " (id " + identity.runnerId() + ")");
    out.println("Organization: " + identity.organizationName());
    out.println("Identity:     " + identityStore.path());
    try {
      Messages.RunnerIdentity me = api().me();
      out.println("Connection:   Connected as " + me.runnerName());
    } catch (ApiClient.ApiError e) {
      out.println("Connection:   " + e.getMessage());
    }
  }

  private void showAssignment() {
    if (!requirePaired()) {
      return;
    }
    try {
      printSummary(api().assignment(), null);
    } catch (ApiClient.ApiError e) {
      out.println(e.getMessage());
    }
  }

  /**
   * Fetches and verifies everything a run needs, shows it, and creates the run on the server.
   *
   * @return the started session, or {@code null} if nothing was started
   */
  private RunSession startRun() throws IOException {
    if (!requirePaired()) {
      return null;
    }
    ApiClient api = api();
    try {
      Messages.Assignment assignment = api.assignment();
      out.println("Downloading and verifying inputs...");
      byte[] seedBytes = inputCache.get(assignment.configuration().sha256(), "yaml", "seed configuration",
          () -> api.content(assignment.configuration().url()));
      byte[] testSetBytes = inputCache.get(assignment.testSet().sha256(), "json", "Test Set",
          () -> api.content(assignment.testSet().url()));

      String seedScript = new String(seedBytes, StandardCharsets.UTF_8);
      Messages.TestSetContent content = Protocol.gson().fromJson(new String(testSetBytes, StandardCharsets.UTF_8),
          Messages.TestSetContent.class);
      List<MatchItem> cases = TestCaseLoader.fromTestSetContent(content);
      if (cases.size() != assignment.testSet().caseCount()) {
        return refuse("The Test Set has " + cases.size() + " cases, but the assignment says "
            + assignment.testSet().caseCount() + ".");
      }
      int[][] weights = ScoringWeights.fromMap(assignment.scoringProfile().weights());
      int[][] seedWeights = ScoringWeights.readFrom(seedScript);

      printSummary(assignment, ScoringWeights.same(weights, seedWeights) ? null
          : "The seed configuration was trained under different weights; this run re-steers it with the profile.");
      String answer = prompt("Start this run? [Y/n] ");
      if (answer == null || !(answer.isEmpty() || answer.toLowerCase(Locale.ROOT).startsWith("y"))) {
        out.println("Not started.");
        return null;
      }

      Messages.RunInfo run = api.createRun(new Messages.CreateRunRequest(assignment.assignmentId(),
          assignment.revision(), assignment.configuration().sha256(), assignment.testSet().snapshotId(),
          assignment.testSet().sha256()));
      // Every candidate carries the fitness function it was trained under (plan D6, item 5).
      String stampedSeed = ScoringWeights.stamp(seedScript, weights);
      RunSession session = new RunSession(api, identity, assignment, run.runId(), stampedSeed, weights, cases,
          checkpoints, line -> {
            out.println();
            out.println(line);
          });
      session.start();
      activeSession = session;
      RunnerLog.LOG.info("Started run " + run.runId() + ": assignment " + assignment.assignmentId() + " revision "
          + assignment.revision() + ", snapshot " + assignment.testSet().snapshotId() + ", population "
          + assignment.populationSize());
      out.println("Starting run " + run.runId() + ". Press Enter for status, 'help' for commands.");
      return session;
    } catch (ApiClient.ApiError e) {
      return refuse(e.getMessage());
    } catch (InputCache.HashMismatch e) {
      return refuse(e.getMessage());
    } catch (RuntimeException e) {
      return refuse("The downloaded inputs could not be read (" + e.getMessage() + ").");
    }
  }

  private RunSession refuse(String reason) {
    RunnerLog.LOG.warning("Unable to start a run: " + reason);
    out.println("Unable to start a run:");
    out.println(reason);
    out.println();
    out.println("No optimization has started.");
    out.println("Use 'start' to try again or 'help' for assistance.");
    return null;
  }

  private void printSummary(Messages.Assignment assignment, String warning) {
    Messages.TestSetRef testSet = assignment.testSet();
    Messages.ScoringProfileInfo profile = assignment.scoringProfile();
    out.println();
    out.println("Runner:        " + identity.runnerName());
    out.println("Assignment:    " + assignment.name() + ", revision " + assignment.revision());
    out.println("Configuration: " + assignment.configuration().label() + " (id " + assignment.configuration().id()
        + ")");
    out.println("Test Set:      " + testSet.label() + " (" + testSet.lifecycleStatus() + "), snapshot "
        + testSet.snapshotId() + ", " + String.format("%,d", testSet.caseCount()) + " cases");
    if ("DRAFT".equals(testSet.lifecycleStatus())) {
      out.println("               Warning: this Test Set is still a draft.");
    }
    out.println("Population:    " + String.format("%,d", assignment.populationSize()));
    out.println("Scoring:       " + profile.name() + " (" + profile.purpose() + ", version " + profile.version() + ")");
    out.print(indent(ScoringWeights.format(ScoringWeights.fromMap(profile.weights()))));
    if (warning != null) {
      out.println("Note:          " + warning);
    }
    out.println();
  }

  // --- running ------------------------------------------------------------------------------

  /** @return {@code true} if the user asked to exit the application */
  private boolean runLoop(RunSession session) throws IOException {
    try {
      return runCommands(session);
    } finally {
      activeSession = null;
    }
  }

  private boolean runCommands(RunSession session) throws IOException {
    Dashboard.print(out, identity, session);
    while (true) {
      String command = prompt("run " + session.runId() + "> ");
      if (session.failure() != null) {
        out.println("Run " + session.runId() + " ended with a failure: " + session.failure());
        out.println("Use 'start' to begin a new run.");
        return false;
      }
      if (command == null) {
        out.println(session.stop("input closed", 30_000L));
        return true;
      }
      switch (command.toLowerCase(Locale.ROOT)) {
      case "":
      case "status":
        Dashboard.print(out, identity, session);
        break;
      case "sync":
        session.sync();
        out.println("Sync: " + session.connection());
        break;
      case "weights":
        out.print(ScoringWeights.format(session.weights()));
        break;
      case "script":
        RunSession.Best best = session.best();
        out.println(best == null ? "No generation has been scored yet." : best.script());
        break;
      case "stop":
        out.println("Stopping after the current step...");
        out.println(session.stop("stopped by user", 30_000L));
        return false;
      case "exit":
      case "quit":
        out.println("Stopping after the current step...");
        out.println(session.stop("runner exited", 30_000L));
        return true;
      case "help":
      case "?":
        out.println(RUNNING_HELP);
        break;
      default:
        out.println("Unknown command '" + command + "'. Type 'help' for commands.");
      }
    }
  }

  // --- helpers ------------------------------------------------------------------------------

  private boolean requirePaired() {
    if (identity == null) {
      out.println("This installation is not paired yet. Type 'pair' first.");
      return false;
    }
    return true;
  }

  private ApiClient api() {
    return new ApiClient(identity.serverUrl(), identity.credential());
  }

  private static String runtimeInfo() {
    return "Java " + System.getProperty("java.version") + "; " + System.getProperty("os.name") + " "
        + System.getProperty("os.version");
  }

  private static String indent(String text) {
    StringBuilder sb = new StringBuilder();
    for (String line : text.split("\n")) {
      sb.append("               ").append(line.replace("\r", "")).append(System.lineSeparator());
    }
    return sb.toString();
  }
}
