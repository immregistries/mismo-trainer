package org.immregistries.mismo.trainer.runner;

import java.util.Date;

import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.optimizer.protocol.ErrorCode;
import org.immregistries.mismo.optimizer.protocol.Hashes;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.OptimizationRun;
import org.immregistries.mismo.trainer.model.Runner;
import org.immregistries.mismo.trainer.model.RunnerAssignment;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.TestSetSnapshot;
import org.immregistries.mismo.trainer.servlet.OrgScope;

/**
 * The Runner API's handlers (docs/island-runner-implementation-plan.md D1, §4), as plain methods
 * that take a Hibernate {@code Session}, the authenticated {@link Runner}, and a request record,
 * and return a response record or throw {@link ApiException}. {@code RunnerApiServlet} does the
 * HTTP, authentication header and transaction; nothing here touches the servlet API.
 *
 * <p>Every read is scoped to the runner's organization: configurations and Test Sets follow
 * {@code OrgScope}'s owned-or-template rule, and runs must belong to the runner itself.
 */
public final class RunnerApi {

  private RunnerApi() {
  }

  /** Version information the runner sends in headers on every request. */
  public record ClientInfo(String clientVersion, String protocolVersion) {
  }

  // --- pairing and identity ---------------------------------------------------------------

  /** {@code POST /runner-pairings}: exchanges a pairing code for a durable credential. */
  public static Messages.PairingResponse pair(Session dataSession, Messages.PairingRequest request) {
    String code = RunnerCredentialSupport.normalizePairingCode(request == null ? null : request.pairingCode());
    if (code == null) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "That pairing code is not valid.");
    }
    Query query = dataSession.createQuery("from Runner where pairingCodeHash = ?");
    query.setParameter(0, RunnerCredentialSupport.hash(code));
    Runner runner = (Runner) query.uniqueResult();
    Date now = new Date();
    if (runner == null || runner.getPairingExpiresAt() == null || runner.getPairingExpiresAt().before(now)) {
      throw new ApiException(ErrorCode.UNAUTHORIZED,
          "That pairing code is not valid or has expired. Create a new one on the runner's page in Mismo Trainer.");
    }
    String credential = RunnerCredentialSupport.generateCredential();
    runner.setCredentialHash(RunnerCredentialSupport.hash(credential));
    runner.setCredentialCreatedAt(now);
    runner.setCredentialLastUsedAt(now);
    runner.setCredentialRevokedAt(null);
    runner.setPairingCodeHash(null);
    runner.setPairingExpiresAt(null);
    runner.setClientVersion(truncate(request.clientVersion(), 30));
    runner.setProtocolVersion(truncate(request.protocolVersion(), 10));
    runner.setRuntimeInfo(truncate(request.runtimeInfo(), 255));
    runner.setLastSeenAt(now);
    dataSession.update(runner);
    return new Messages.PairingResponse(runner.getRunnerId(), runner.getDisplayName(),
        runner.getOrganization().getName(), credential);
  }

  /**
   * Resolves a bearer credential to its runner and records the contact.
   *
   * @throws ApiException {@code UNAUTHORIZED} if the credential is missing, unknown or revoked
   */
  public static Runner authenticate(Session dataSession, String credential, ClientInfo clientInfo) {
    if (credential == null || credential.isBlank()) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "This runner is not paired.");
    }
    Query query = dataSession.createQuery("from Runner where credentialHash = ?");
    query.setParameter(0, RunnerCredentialSupport.hash(credential.trim()));
    Runner runner = (Runner) query.uniqueResult();
    if (runner == null || runner.getCredentialRevokedAt() != null) {
      throw new ApiException(ErrorCode.UNAUTHORIZED,
          "This runner's credential is not recognized or was revoked. Pair it again.");
    }
    Date now = new Date();
    runner.setCredentialLastUsedAt(now);
    runner.setLastSeenAt(now);
    if (clientInfo.clientVersion() != null) {
      runner.setClientVersion(truncate(clientInfo.clientVersion(), 30));
    }
    if (clientInfo.protocolVersion() != null) {
      runner.setProtocolVersion(truncate(clientInfo.protocolVersion(), 10));
    }
    dataSession.update(runner);
    return runner;
  }

  /** {@code GET /runners/me} */
  public static Messages.RunnerIdentity me(Runner runner) {
    return new Messages.RunnerIdentity(runner.getRunnerId(), runner.getDisplayName(),
        runner.getOrganization().getName());
  }

  // --- assignment and content -------------------------------------------------------------

  /**
   * {@code GET /runners/me/assignment}: the runner's current assignment, snapshotting the Test Set
   * as it is now (D3). Called only while idle, just before a run starts.
   */
  public static Messages.Assignment assignment(Session dataSession, Runner runner) {
    RunnerAssignment assignment = RunnerScope.currentAssignment(dataSession, runner);
    if (assignment == null) {
      throw new ApiException(ErrorCode.NO_ASSIGNMENT,
          "This runner has no assignment yet. Set one on its page in Mismo Trainer.");
    }
    Configuration configuration = OrgScope.loadConfiguration(dataSession,
        assignment.getConfiguration().getConfigurationId(), runner.getOrganization());
    MatchSet matchSet = OrgScope.loadMatchSet(dataSession, assignment.getMatchSet().getMatchSetId(),
        runner.getOrganization());
    ScoringProfile profile = RunnerScope.loadScoringProfile(dataSession,
        assignment.getScoringProfile().getScoringProfileId(), runner.getOrganization());
    if (configuration == null || matchSet == null || profile == null) {
      throw new ApiException(ErrorCode.ASSIGNMENT_INVALID,
          "The assigned configuration, Test Set or scoring profile is no longer available to this runner's"
              + " organization. Update the assignment in Mismo Trainer.");
    }
    TestSetSnapshot snapshot = TestSetSnapshots.snapshot(dataSession, matchSet);
    if (snapshot.getCaseCount() == 0) {
      throw new ApiException(ErrorCode.ASSIGNMENT_INVALID,
          "The assigned Test Set has no cases with a Match, Possible Match or Not a Match expected status.");
    }
    return new Messages.Assignment(assignment.getRunnerAssignmentId(), assignment.getRevision(), assignment.getName(),
        new Messages.ConfigurationRef(configuration.getConfigurationId(), configurationLabel(configuration),
            contentSha256(configuration), "/configurations/" + configuration.getConfigurationId() + "/content"),
        new Messages.TestSetRef(matchSet.getMatchSetId(), matchSet.getLabel(), snapshot.getLifecycleStatus(),
            snapshot.getTestSetSnapshotId(), snapshot.getContentSha256(), snapshot.getCaseCount(),
            "/test-set-snapshots/" + snapshot.getTestSetSnapshotId() + "/content"),
        assignment.getPopulationSize(), profileInfo(profile));
  }

  /** {@code GET /configurations/{id}/content}: the exact stored script. */
  public static String configurationContent(Session dataSession, Runner runner, int configurationId) {
    Configuration configuration = OrgScope.loadConfiguration(dataSession, configurationId, runner.getOrganization());
    if (configuration == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Configuration " + configurationId + " was not found.");
    }
    return configuration.getConfigurationScript();
  }

  /** {@code GET /test-set-snapshots/{id}/content}: the exact stored JSON. */
  public static String testSetSnapshotContent(Session dataSession, Runner runner, int snapshotId) {
    TestSetSnapshot snapshot = (TestSetSnapshot) dataSession.get(TestSetSnapshot.class, snapshotId);
    if (snapshot == null
        || OrgScope.loadMatchSet(dataSession, snapshot.getMatchSet().getMatchSetId(), runner.getOrganization()) == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Test Set snapshot " + snapshotId + " was not found.");
    }
    return snapshot.getContentJson();
  }

  // --- runs ---------------------------------------------------------------------------------

  /**
   * {@code POST /runs}: creates a {@code RUNNING} run from the current assignment revision after
   * checking that the runner downloaded exactly what the assignment names. A run of this runner
   * still marked {@code RUNNING} (its process went away without stopping) is marked
   * {@code ABANDONED}.
   */
  public static Messages.RunInfo createRun(Session dataSession, Runner runner, Messages.CreateRunRequest request,
      ClientInfo clientInfo) {
    if (request == null) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "A request body is required.");
    }
    RunnerAssignment current = RunnerScope.currentAssignment(dataSession, runner);
    if (current == null || current.getRunnerAssignmentId() != request.assignmentId()
        || current.getRevision() != request.revision()) {
      throw new ApiException(ErrorCode.ASSIGNMENT_CHANGED,
          "The assignment changed in Mismo Trainer after it was downloaded. Fetch it again before starting.");
    }
    Configuration seed = current.getConfiguration();
    String seedSha256 = contentSha256(seed);
    if (!seedSha256.equals(request.configurationSha256())) {
      throw new ApiException(ErrorCode.HASH_MISMATCH, "The seed configuration does not match the assignment.");
    }
    TestSetSnapshot snapshot = (TestSetSnapshot) dataSession.get(TestSetSnapshot.class, request.testSetSnapshotId());
    if (snapshot == null || snapshot.getMatchSet().getMatchSetId() != current.getMatchSet().getMatchSetId()
        || !snapshot.getContentSha256().equals(request.testSetSha256())) {
      throw new ApiException(ErrorCode.HASH_MISMATCH, "The Test Set snapshot does not match the assignment.");
    }

    Date now = new Date();
    OptimizationRun stale = RunnerScope.activeRun(dataSession, runner);
    while (stale != null) {
      stale.setStatus(Protocol.RUN_ABANDONED);
      stale.setStopReason("superseded");
      stale.setStoppedAt(now);
      dataSession.update(stale);
      dataSession.flush();
      stale = RunnerScope.activeRun(dataSession, runner);
    }

    ScoringProfile profile = current.getScoringProfile();
    OptimizationRun run = new OptimizationRun();
    run.setRunner(runner);
    run.setRunnerAssignment(current);
    run.setSeedConfiguration(seed);
    run.setSeedConfigurationSha256(seedSha256);
    run.setTestSetSnapshot(snapshot);
    run.setPopulationSize(current.getPopulationSize());
    run.setScoringProfile(profile);
    run.setScoringWeightsJson(profile.getScoringWeightsJson());
    run.setClientVersion(clientInfo.clientVersion() == null ? "unknown" : truncate(clientInfo.clientVersion(), 30));
    run.setProtocolVersion(
        clientInfo.protocolVersion() == null ? Protocol.PROTOCOL_VERSION : truncate(clientInfo.protocolVersion(), 10));
    run.setStatus(Protocol.RUN_RUNNING);
    run.setStartedAt(now);
    run.setLastReportedAt(now);
    dataSession.save(run);
    return runInfo(run);
  }

  /** {@code GET /runs/{id}} */
  public static Messages.RunInfo getRun(Session dataSession, Runner runner, int runId) {
    return runInfo(loadOwnRun(dataSession, runner, runId));
  }

  /** {@code POST /runs/{id}/progress}. Acknowledges only; never carries new run inputs. */
  public static Messages.RunInfo progress(Session dataSession, Runner runner, int runId,
      Messages.ProgressReport report) {
    OptimizationRun run = loadActiveRun(dataSession, runner, runId);
    if (report == null) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "A request body is required.");
    }
    run.setLastGeneration(report.generation());
    run.setCandidateRate(report.candidateRate());
    if (run.getBestScore() == null || report.bestScore() > run.getBestScore()) {
      run.setBestScore(report.bestScore());
    }
    run.setLastReportedAt(new Date());
    dataSession.update(run);
    return runInfo(run);
  }

  /**
   * {@code POST /runs/{id}/candidates}: stores a candidate as a configuration row tied to the run.
   * Re-sending the same script returns the existing row. The candidate must carry the run's
   * scoring weights (D6), so every stored candidate records the fitness function it was trained
   * under.
   */
  public static Messages.CandidateReceipt candidate(Session dataSession, Runner runner, int runId,
      Messages.CandidateUpload upload) {
    OptimizationRun run = loadActiveRun(dataSession, runner, runId);
    if (upload == null || upload.configurationScript() == null || upload.configurationScript().isBlank()) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "A configuration script is required.");
    }
    String script = upload.configurationScript();
    String sha256 = Hashes.sha256Hex(script);
    Query query = dataSession.createQuery("from Configuration where optimizationRun = ? and contentSha256 = ?");
    query.setParameter(0, run);
    query.setParameter(1, sha256);
    Configuration existing = (Configuration) query.uniqueResult();
    if (existing != null) {
      return new Messages.CandidateReceipt(existing.getConfigurationId(), sha256, false);
    }

    org.immregistries.mismo.match.model.Configuration parsed;
    try {
      parsed = new org.immregistries.mismo.match.model.Configuration(script);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.INVALID_CANDIDATE, "The candidate is not a readable Mismo configuration.");
    }
    if (!ScoringWeights.same(parsed.getScoringWeights(), ScoringWeights.fromJson(run.getScoringWeightsJson()))) {
      throw new ApiException(ErrorCode.INVALID_CANDIDATE,
          "The candidate's scoringWeights do not match this run's scoring profile.");
    }

    Date now = new Date();
    Configuration configuration = new Configuration();
    // Columns come from the server's own records (D4); the script keeps whatever the runner wrote.
    configuration.setWorldName(run.getRunnerAssignment().getName());
    configuration.setIslandName(runner.getDisplayName());
    configuration.setGeneration(upload.generation());
    configuration.setGenerationScore(upload.score());
    configuration.setGeneratedDate(parsed.getGeneratedDate() == null ? now : parsed.getGeneratedDate());
    configuration.setHashForSignature(parsed.getHashForSignature());
    configuration.setConfigurationScript(script);
    configuration.setContentSha256(sha256);
    configuration.setOrganization(runner.getOrganization());
    configuration.setTemplate(false);
    configuration.setCreatedAt(now);
    configuration.setOptimizationRun(run);
    dataSession.save(configuration);

    if (run.getBestConfiguration() == null || run.getBestScore() == null || upload.score() >= run.getBestScore()) {
      run.setBestConfiguration(configuration);
      run.setBestScore(upload.score());
    }
    if (run.getLastGeneration() == null || upload.generation() > run.getLastGeneration()) {
      run.setLastGeneration(upload.generation());
    }
    run.setLastReportedAt(now);
    dataSession.update(run);
    return new Messages.CandidateReceipt(configuration.getConfigurationId(), sha256, true);
  }

  /** {@code POST /runs/{id}/stop}. Stopping a run that is no longer running just returns it. */
  public static Messages.RunInfo stop(Session dataSession, Runner runner, int runId, Messages.StopRequest request) {
    return end(dataSession, runner, runId, Protocol.RUN_STOPPED, request, "stopped");
  }

  /**
   * {@code POST /runs/{id}/fail}: the local optimization failed, or the runner found a run it
   * never stopped (a crash). Like {@link #stop}, ending a run that is no longer running just
   * returns it.
   */
  public static Messages.RunInfo fail(Session dataSession, Runner runner, int runId, Messages.StopRequest request) {
    return end(dataSession, runner, runId, Protocol.RUN_FAILED, request, "failed");
  }

  private static Messages.RunInfo end(Session dataSession, Runner runner, int runId, String status,
      Messages.StopRequest request, String defaultReason) {
    OptimizationRun run = loadOwnRun(dataSession, runner, runId);
    if (Protocol.RUN_RUNNING.equals(run.getStatus())) {
      Date now = new Date();
      run.setStatus(status);
      run.setStopReason(truncate(request == null || request.reason() == null ? defaultReason : request.reason(), 255));
      run.setStoppedAt(now);
      run.setLastReportedAt(now);
      dataSession.update(run);
    }
    return runInfo(run);
  }

  // --- versions -----------------------------------------------------------------------------

  /** {@code app_setting} key holding the oldest runner version the server accepts. */
  public static final String SETTING_MIN_CLIENT_VERSION = "runner.min.client.version";

  /**
   * Refuses a client whose protocol isn't this server's, or whose version is below
   * {@code minimumVersion} (docs/island-runner-implementation-plan.md §2).
   *
   * @param minimumVersion {@code null} or blank for no minimum
   * @throws ApiException {@code CLIENT_UNSUPPORTED}
   */
  public static void checkClientVersion(ClientInfo clientInfo, String minimumVersion) {
    if (clientInfo.protocolVersion() != null && !Protocol.PROTOCOL_VERSION.equals(clientInfo.protocolVersion())) {
      throw new ApiException(ErrorCode.CLIENT_UNSUPPORTED, "This runner speaks protocol " + clientInfo.protocolVersion()
          + ", but Mismo Trainer speaks protocol " + Protocol.PROTOCOL_VERSION
          + ". Download the current Mismo Runner from Mismo Trainer.");
    }
    if (minimumVersion == null || minimumVersion.isBlank()) {
      return;
    }
    if (clientInfo.clientVersion() == null || compareVersions(clientInfo.clientVersion(), minimumVersion) < 0) {
      throw new ApiException(ErrorCode.CLIENT_UNSUPPORTED, "This Mismo Runner ("
          + (clientInfo.clientVersion() == null ? "unknown version" : "version " + clientInfo.clientVersion())
          + ") is too old; version " + minimumVersion.trim()
          + " or later is required. Download the current Mismo Runner from Mismo Trainer.");
    }
  }

  /**
   * Compares dotted versions numerically ("2.10" is newer than "2.9"). Non-numeric parts
   * (e.g. "dev", "-SNAPSHOT") count as 0, so a development build is treated as oldest.
   */
  public static int compareVersions(String a, String b) {
    String[] partsA = a.trim().split("\\.");
    String[] partsB = b.trim().split("\\.");
    for (int i = 0; i < Math.max(partsA.length, partsB.length); i++) {
      int diff = Integer.compare(versionPart(partsA, i), versionPart(partsB, i));
      if (diff != 0) {
        return diff;
      }
    }
    return 0;
  }

  private static int versionPart(String[] parts, int i) {
    if (i >= parts.length) {
      return 0;
    }
    String digits = parts[i].replaceAll("^(\\d*).*$", "$1");
    return digits.isEmpty() ? 0 : Integer.parseInt(digits);
  }

  // --- helpers ------------------------------------------------------------------------------

  private static OptimizationRun loadOwnRun(Session dataSession, Runner runner, int runId) {
    OptimizationRun run = (OptimizationRun) dataSession.get(OptimizationRun.class, runId);
    if (run == null || run.getRunner().getRunnerId() != runner.getRunnerId()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Run " + runId + " was not found.");
    }
    return run;
  }

  private static OptimizationRun loadActiveRun(Session dataSession, Runner runner, int runId) {
    OptimizationRun run = loadOwnRun(dataSession, runner, runId);
    if (!Protocol.RUN_RUNNING.equals(run.getStatus())) {
      throw new ApiException(ErrorCode.RUN_NOT_ACTIVE,
          "Run " + runId + " is " + run.getStatus() + " in Mismo Trainer, so it no longer accepts reports.");
    }
    return run;
  }

  private static Messages.RunInfo runInfo(OptimizationRun run) {
    return new Messages.RunInfo(run.getOptimizationRunId(), run.getStatus(),
        run.getRunnerAssignment().getRunnerAssignmentId(), run.getRunnerAssignment().getRevision(),
        run.getLastGeneration(), run.getBestScore(),
        run.getBestConfiguration() == null ? null : run.getBestConfiguration().getConfigurationId());
  }

  static Messages.ScoringProfileInfo profileInfo(ScoringProfile profile) {
    return new Messages.ScoringProfileInfo(profile.getScoringProfileId(), profile.getVersion(), profile.getName(),
        profile.getPurpose(), ScoringWeights.toMap(ScoringWeights.fromJson(profile.getScoringWeightsJson())));
  }

  /** The stored hash, or computed from the script for a row saved before the column existed. */
  public static String contentSha256(Configuration configuration) {
    return configuration.getContentSha256() != null ? configuration.getContentSha256()
        : Hashes.sha256Hex(configuration.getConfigurationScript());
  }

  public static String configurationLabel(Configuration configuration) {
    return configuration.getWorldName() + " / " + configuration.getIslandName() + " (gen "
        + configuration.getGeneration() + ")";
  }

  private static String truncate(String value, int length) {
    return value == null || value.length() <= length ? value : value.substring(0, length);
  }
}
