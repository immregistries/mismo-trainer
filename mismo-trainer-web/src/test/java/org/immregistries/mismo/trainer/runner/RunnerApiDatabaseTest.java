package org.immregistries.mismo.trainer.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.UUID;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.Scorer;
import org.immregistries.mismo.optimizer.protocol.ErrorCode;
import org.immregistries.mismo.optimizer.protocol.Hashes;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.HibernateSessionFactorySupport;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.OptimizationRun;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.Runner;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.User;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Runner API handlers against the local MySQL dev database (docs/island-runner-implementation-plan.md
 * §8). Every test runs in one transaction that is rolled back, and builds its own organizations,
 * users, configurations and Test Sets, so nothing is left behind and existing data isn't relied
 * on. Opt-in: runs only when {@code MISMO_TEST_DB_USER}/{@code MISMO_TEST_DB_PASSWORD} are set
 * (to an account with access to the {@code mismo} database; {@code MISMO_DB_URL} as for the app),
 * so a plain {@code mvn package} never needs a database.
 */
public class RunnerApiDatabaseTest {

  private static final RunnerApi.ClientInfo CLIENT = new RunnerApi.ClientInfo("2.0", Protocol.PROTOCOL_VERSION);

  private static SessionFactory factory;

  private Session session;
  private Transaction transaction;
  private Organization orgA;
  private Organization orgB;
  private User userA;
  private User userB;
  private ScoringProfile profileA;

  @BeforeClass
  public static void buildFactory() {
    Assume.assumeTrue("MISMO_TEST_DB_USER/MISMO_TEST_DB_PASSWORD not set; skipping database tests",
        isSet("MISMO_TEST_DB_USER") && isSet("MISMO_TEST_DB_PASSWORD"));
    factory = HibernateSessionFactorySupport.build(System.getenv("MISMO_TEST_DB_USER"),
        System.getenv("MISMO_TEST_DB_PASSWORD"));
  }

  private static boolean isSet(String name) {
    String value = System.getenv(name);
    return value != null && !value.isBlank();
  }

  @AfterClass
  public static void closeFactory() {
    if (factory != null) {
      factory.close();
    }
  }

  @Before
  public void begin() {
    session = factory.openSession();
    transaction = session.beginTransaction();
    orgA = organization("A");
    orgB = organization("B");
    userA = user(orgA);
    userB = user(orgB);
    profileA = profile(orgA, Scorer.defaultWeights());
  }

  @After
  public void rollback() {
    if (transaction != null) {
      transaction.rollback();
    }
    if (session != null) {
      session.close();
    }
  }

  // --- pairing and authentication -----------------------------------------------------------

  @Test
  public void pairingCodeWorksOnceAndCredentialAuthenticates() {
    RunnerScope.CreatedRunner created = RunnerScope.createRunner(session, "Laptop", userA);
    Messages.PairingResponse paired = RunnerApi.pair(session, pairing(created.pairingCode()));
    assertEquals("Laptop", paired.runnerName());
    assertTrue(paired.credential().startsWith("mrn_"));

    Runner runner = RunnerApi.authenticate(session, paired.credential(), CLIENT);
    assertEquals(created.runner().getRunnerId(), runner.getRunnerId());
    assertNotNull(runner.getLastSeenAt());

    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.pair(session, pairing(created.pairingCode())));
  }

  @Test
  public void expiredPairingCodeIsRefused() {
    RunnerScope.CreatedRunner created = RunnerScope.createRunner(session, "Laptop", userA);
    created.runner().setPairingExpiresAt(new Date(System.currentTimeMillis() - 1000));
    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.pair(session, pairing(created.pairingCode())));
  }

  @Test
  public void missingUnknownAndRevokedCredentialsAreRefused() {
    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.authenticate(session, null, CLIENT));
    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.authenticate(session, "mrn_not-a-real-credential", CLIENT));

    RunnerScope.CreatedRunner created = RunnerScope.createRunner(session, "Laptop", userA);
    String credential = RunnerApi.pair(session, pairing(created.pairingCode())).credential();
    RunnerScope.revokeCredential(created.runner());
    session.flush();
    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.authenticate(session, credential, CLIENT));

    // Pairing again with a new code restores access, with a new credential.
    String code = RunnerScope.setNewPairingCode(created.runner());
    String newCredential = RunnerApi.pair(session, pairing(code)).credential();
    assertNotEquals(credential, newCredential);
    RunnerApi.authenticate(session, newCredential, CLIENT);
    expect(ErrorCode.UNAUTHORIZED, () -> RunnerApi.authenticate(session, credential, CLIENT));
  }

  // --- organization isolation ---------------------------------------------------------------

  @Test
  public void runnerReadsOnlyItsOrganizationsDataAndTemplates() {
    Runner runnerA = pairedRunner(userA);
    Runner runnerB = pairedRunner(userB);
    Configuration privateB = configuration(orgB, false);
    Configuration templateB = configuration(orgB, true);
    MatchSet setB = matchSet(orgB, false);

    expect(ErrorCode.NOT_FOUND,
        () -> RunnerApi.configurationContent(session, runnerA, privateB.getConfigurationId()));
    assertEquals(templateB.getConfigurationScript(),
        RunnerApi.configurationContent(session, runnerA, templateB.getConfigurationId()));

    // B's own snapshot is readable by B, not by A.
    ScoringProfile profileB = profile(orgB, Scorer.defaultWeights());
    RunnerScope.saveAssignment(session, runnerB, "B", privateB, setB, 100, profileB, userB);
    Messages.Assignment assignmentB = RunnerApi.assignment(session, runnerB);
    int snapshotB = assignmentB.testSet().snapshotId();
    RunnerApi.testSetSnapshotContent(session, runnerB, snapshotB);
    expect(ErrorCode.NOT_FOUND, () -> RunnerApi.testSetSnapshotContent(session, runnerA, snapshotB));

    // B's runs are invisible to A.
    Messages.RunInfo runB = RunnerApi.createRun(session, runnerB, createRequest(assignmentB), CLIENT);
    expect(ErrorCode.NOT_FOUND, () -> RunnerApi.getRun(session, runnerA, runB.runId()));
    expect(ErrorCode.NOT_FOUND, () -> RunnerApi.progress(session, runnerA, runB.runId(),
        new Messages.ProgressReport(1, 1.0, 0.5)));
    expect(ErrorCode.NOT_FOUND, () -> RunnerApi.stop(session, runnerA, runB.runId(), null));
  }

  @Test
  public void assignmentToAnotherOrganizationsPrivateDataIsInvalid() {
    Runner runnerA = pairedRunner(userA);
    // The website never offers this, but if B's set is made private after being assigned...
    RunnerScope.saveAssignment(session, runnerA, "A", configuration(orgA, false), matchSet(orgB, false), 100,
        profileA, userA);
    expect(ErrorCode.ASSIGNMENT_INVALID, () -> RunnerApi.assignment(session, runnerA));
  }

  @Test
  public void noAssignmentIsReported() {
    Runner runnerA = pairedRunner(userA);
    expect(ErrorCode.NO_ASSIGNMENT, () -> RunnerApi.assignment(session, runnerA));
  }

  // --- snapshots and runs -------------------------------------------------------------------

  @Test
  public void snapshotIsReusedUntilACaseChanges() {
    Runner runner = pairedRunner(userA);
    MatchSet set = matchSet(orgA, false);
    RunnerScope.saveAssignment(session, runner, "A", configuration(orgA, false), set, 100, profileA, userA);

    Messages.Assignment first = RunnerApi.assignment(session, runner);
    assertEquals(3, first.testSet().caseCount()); // the Research case is left out
    assertEquals(first.testSet().snapshotId(), RunnerApi.assignment(session, runner).testSet().snapshotId());
    String content = RunnerApi.testSetSnapshotContent(session, runner, first.testSet().snapshotId());
    assertEquals(first.testSet().sha256(), Hashes.sha256Hex(content));

    org.immregistries.mismo.trainer.model.MatchItem item = item(set, "S-9", MatchItem.MATCH);
    session.flush();
    Messages.Assignment edited = RunnerApi.assignment(session, runner);
    assertNotEquals(first.testSet().snapshotId(), edited.testSet().snapshotId());
    assertEquals(4, edited.testSet().caseCount());

    session.delete(item);
    session.flush();
    assertEquals("Same content as the first snapshot, so it is reused", first.testSet().snapshotId(),
        RunnerApi.assignment(session, runner).testSet().snapshotId());
  }

  @Test
  public void runNeedsTheCurrentRevisionAndMatchingHashes() {
    Runner runner = pairedRunner(userA);
    Configuration seed = configuration(orgA, false);
    MatchSet set = matchSet(orgA, false);
    RunnerScope.saveAssignment(session, runner, "rev 1", seed, set, 100, profileA, userA);
    Messages.Assignment rev1 = RunnerApi.assignment(session, runner);

    Messages.CreateRunRequest good = createRequest(rev1);
    expect(ErrorCode.HASH_MISMATCH, () -> RunnerApi.createRun(session, runner, new Messages.CreateRunRequest(
        good.assignmentId(), good.revision(), "0".repeat(64), good.testSetSnapshotId(), good.testSetSha256()), CLIENT));
    expect(ErrorCode.HASH_MISMATCH, () -> RunnerApi.createRun(session, runner, new Messages.CreateRunRequest(
        good.assignmentId(), good.revision(), good.configurationSha256(), good.testSetSnapshotId(), "0".repeat(64)),
        CLIENT));

    RunnerScope.saveAssignment(session, runner, "rev 2", seed, set, 150, profileA, userA);
    expect(ErrorCode.ASSIGNMENT_CHANGED, () -> RunnerApi.createRun(session, runner, good, CLIENT));

    Messages.Assignment rev2 = RunnerApi.assignment(session, runner);
    assertEquals(2, rev2.revision());
    Messages.RunInfo run = RunnerApi.createRun(session, runner, createRequest(rev2), CLIENT);
    assertEquals(Protocol.RUN_RUNNING, run.status());
    assertEquals(2, run.revision());
  }

  @Test
  public void newRunAbandonsAStaleRunningRun() {
    Runner runner = pairedRunner(userA);
    RunnerScope.saveAssignment(session, runner, "A", configuration(orgA, false), matchSet(orgA, false), 100,
        profileA, userA);
    Messages.Assignment assignment = RunnerApi.assignment(session, runner);
    Messages.RunInfo crashed = RunnerApi.createRun(session, runner, createRequest(assignment), CLIENT);
    Messages.RunInfo next = RunnerApi.createRun(session, runner, createRequest(assignment), CLIENT);

    OptimizationRun stale = (OptimizationRun) session.get(OptimizationRun.class, crashed.runId());
    assertEquals(Protocol.RUN_ABANDONED, stale.getStatus());
    assertEquals("superseded", stale.getStopReason());
    assertEquals(Protocol.RUN_RUNNING, RunnerApi.getRun(session, runner, next.runId()).status());
  }

  @Test
  public void candidatesAreIdempotentAndMustCarryTheRunsWeights() {
    Runner runner = pairedRunner(userA);
    Configuration seed = configuration(orgA, false);
    RunnerScope.saveAssignment(session, runner, "A", seed, matchSet(orgA, false), 100, profileA, userA);
    Messages.Assignment assignment = RunnerApi.assignment(session, runner);
    int runId = RunnerApi.createRun(session, runner, createRequest(assignment), CLIENT).runId();

    String script = seed.getConfigurationScript();
    Messages.CandidateReceipt first = RunnerApi.candidate(session, runner, runId,
        new Messages.CandidateUpload(3, 0.7, script));
    assertTrue(first.created());
    Messages.CandidateReceipt again = RunnerApi.candidate(session, runner, runId,
        new Messages.CandidateUpload(3, 0.7, script));
    assertFalse(again.created());
    assertEquals(first.configurationId(), again.configurationId());

    Configuration stored = (Configuration) session.get(Configuration.class, first.configurationId());
    assertEquals("A", stored.getWorldName());
    assertEquals(runner.getDisplayName(), stored.getIslandName());
    assertEquals(Hashes.sha256Hex(script), stored.getContentSha256());
    assertEquals(first.configurationId(), (int) RunnerApi.getRun(session, runner, runId).bestConfigurationId());

    String otherWeights = ScoringWeights.stamp(script, new int[][] { { 1, 1, 1 }, { 1, 1, 1 }, { 1, 1, 1 } });
    expect(ErrorCode.INVALID_CANDIDATE, () -> RunnerApi.candidate(session, runner, runId,
        new Messages.CandidateUpload(4, 0.9, otherWeights)));
    expect(ErrorCode.INVALID_CANDIDATE, () -> RunnerApi.candidate(session, runner, runId,
        new Messages.CandidateUpload(4, 0.9, "not: [a configuration")));
  }

  @Test
  public void stoppedAndFailedRunsAcceptNoReports() {
    Runner runner = pairedRunner(userA);
    RunnerScope.saveAssignment(session, runner, "A", configuration(orgA, false), matchSet(orgA, false), 100,
        profileA, userA);
    Messages.Assignment assignment = RunnerApi.assignment(session, runner);

    int stopped = RunnerApi.createRun(session, runner, createRequest(assignment), CLIENT).runId();
    assertEquals(Protocol.RUN_STOPPED, RunnerApi.stop(session, runner, stopped, null).status());
    expect(ErrorCode.RUN_NOT_ACTIVE, () -> RunnerApi.progress(session, runner, stopped,
        new Messages.ProgressReport(1, 1.0, 0.5)));

    int failed = RunnerApi.createRun(session, runner, createRequest(assignment), CLIENT).runId();
    Messages.RunInfo info = RunnerApi.fail(session, runner, failed, new Messages.StopRequest("runner restarted"));
    assertEquals(Protocol.RUN_FAILED, info.status());
    assertEquals("runner restarted", ((OptimizationRun) session.get(OptimizationRun.class, failed)).getStopReason());
    // Ending an ended run changes nothing.
    assertEquals(Protocol.RUN_FAILED, RunnerApi.stop(session, runner, failed, null).status());
  }

  @Test
  public void clientVersionCheck() {
    RunnerApi.checkClientVersion(CLIENT, "2.0");
    RunnerApi.checkClientVersion(new RunnerApi.ClientInfo("2.10", "1"), "2.9");
    RunnerApi.checkClientVersion(new RunnerApi.ClientInfo("1.0", "1"), null);
    expect(ErrorCode.CLIENT_UNSUPPORTED, () -> RunnerApi.checkClientVersion(new RunnerApi.ClientInfo("1.9", "1"), "2.0"));
    expect(ErrorCode.CLIENT_UNSUPPORTED, () -> RunnerApi.checkClientVersion(new RunnerApi.ClientInfo(null, "1"), "2.0"));
    expect(ErrorCode.CLIENT_UNSUPPORTED, () -> RunnerApi.checkClientVersion(new RunnerApi.ClientInfo("2.0", "2"), null));
  }

  // --- Slice 3: profiles, abandon, results ----------------------------------------------------

  @Test
  public void profileEditsAddVersionsOnlyForTheOwner() {
    ScoringProfile v2 = RunnerScope.saveProfile(session, profileA, "Test profile", RunnerScope.PURPOSE_EXPERIMENTAL,
        "Leans harder against false matches", new int[][] { { 12, 4, -10 }, { 4, 10, -8 }, { -20, -10, 10 } }, userA);
    assertEquals(2, v2.getVersion());
    assertEquals(profileA.getRootScoringProfileId(), v2.getRootScoringProfileId());
    assertEquals(2, RunnerScope.listProfileVersions(session, profileA.getRootScoringProfileId(), orgA).size());
    // The old version is untouched, so a run that used it keeps its weights.
    assertEquals(ScoringWeights.toJson(Scorer.defaultWeights()), profileA.getScoringWeightsJson());

    assertTrue(RunnerScope.isEditable(profileA, userA));
    assertFalse(RunnerScope.isEditable(profileA, userB));
    assertEquals(null, RunnerScope.loadScoringProfile(session, profileA.getScoringProfileId(), orgB));
    assertTrue(RunnerScope.listProfileVersions(session, profileA.getRootScoringProfileId(), orgB).isEmpty());
  }

  @Test
  public void abandonedRunRejectsReportsAndIsScopedToItsOrganization() {
    Runner runner = pairedRunner(userA);
    RunnerScope.saveAssignment(session, runner, "A", configuration(orgA, false), matchSet(orgA, false), 100,
        profileA, userA);
    int runId = RunnerApi.createRun(session, runner, createRequest(RunnerApi.assignment(session, runner)), CLIENT)
        .runId();
    assertEquals(null, RunnerScope.loadRun(session, runId, userB));
    OptimizationRun run = RunnerScope.loadRun(session, runId, userA);
    assertTrue(RunnerScope.abandonRun(run, userA));
    assertFalse("only a running run can be abandoned", RunnerScope.abandonRun(run, userA));
    session.flush();
    expect(ErrorCode.RUN_NOT_ACTIVE, () -> RunnerApi.progress(session, runner, runId,
        new Messages.ProgressReport(1, 1.0, 0.5)));
  }

  @Test
  public void candidateResultsCollapseToPotentialMatch() {
    Runner runner = pairedRunner(userA);
    Configuration seed = configuration(orgA, false);
    RunnerScope.saveAssignment(session, runner, "A", seed, matchSet(orgA, false), 100, profileA, userA);
    int runId = RunnerApi.createRun(session, runner, createRequest(RunnerApi.assignment(session, runner)), CLIENT)
        .runId();
    OptimizationRun run = (OptimizationRun) session.get(OptimizationRun.class, runId);

    CandidateScoring.Result result = CandidateScoring.score(run, seed);
    int total = 0;
    for (int[] row : result.counts()) {
      for (int count : row) {
        total += count;
      }
    }
    assertEquals("the Research case is not scored", 3, total);
    int[][] potential = result.potentialMatchTable();
    assertEquals(result.counts()[0][0] + result.counts()[0][1] + result.counts()[1][0] + result.counts()[1][1],
        potential[0][0]);
    assertEquals(result.counts()[2][2], potential[1][1]);
  }

  @Test
  public void derivedRunnerState() {
    RunnerScope.CreatedRunner created = RunnerScope.createRunner(session, "Laptop", userA);
    Runner runner = created.runner();
    long now = System.currentTimeMillis();
    assertEquals(RunnerScope.State.NOT_PAIRED, RunnerScope.state(runner, null, now));
    RunnerApi.pair(session, pairing(created.pairingCode()));
    assertEquals(RunnerScope.State.IDLE, RunnerScope.state(runner, null, now));
    assertEquals(RunnerScope.State.RUNNING, RunnerScope.state(runner, new OptimizationRun(), now));
    assertEquals(RunnerScope.State.OFFLINE,
        RunnerScope.state(runner, null, now + RunnerScope.SEEN_WINDOW_MILLIS + 1000));
    RunnerScope.revokeCredential(runner);
    assertEquals(RunnerScope.State.REVOKED, RunnerScope.state(runner, null, now));
  }

  // --- fixtures -----------------------------------------------------------------------------

  private interface Call {
    void run();
  }

  private static void expect(ErrorCode code, Call call) {
    try {
      call.run();
      fail("Expected " + code);
    } catch (ApiException e) {
      assertEquals(e.getMessage(), code, e.getCode());
    }
  }

  private static Messages.PairingRequest pairing(String code) {
    return new Messages.PairingRequest(code, "2.0", Protocol.PROTOCOL_VERSION, "test");
  }

  private static Messages.CreateRunRequest createRequest(Messages.Assignment assignment) {
    return new Messages.CreateRunRequest(assignment.assignmentId(), assignment.revision(),
        assignment.configuration().sha256(), assignment.testSet().snapshotId(), assignment.testSet().sha256());
  }

  private Runner pairedRunner(User user) {
    RunnerScope.CreatedRunner created = RunnerScope.createRunner(session, "Runner " + user.getUserId(), user);
    RunnerApi.pair(session, pairing(created.pairingCode()));
    return created.runner();
  }

  private Organization organization(String name) {
    Organization organization = new Organization();
    organization.setName("Test org " + name);
    organization.setDomain("test-" + UUID.randomUUID() + ".invalid");
    organization.setTemplateOrg(false);
    organization.setCreatedAt(new Date());
    organization.setUpdatedAt(new Date());
    session.save(organization);
    return organization;
  }

  private User user(Organization organization) {
    User user = new User();
    user.setEmail("runner-test@" + organization.getDomain());
    user.setHubUserId("test-" + UUID.randomUUID());
    user.setOrganization(organization);
    user.setCreatedAt(new Date());
    session.save(user);
    return user;
  }

  private ScoringProfile profile(Organization organization, int[][] weights) {
    ScoringProfile profile = new ScoringProfile();
    profile.setRootScoringProfileId(0);
    profile.setVersion(1);
    profile.setName("Test profile");
    profile.setPurpose(RunnerScope.PURPOSE_MEASUREMENT);
    profile.setDescription("Test");
    profile.setScoringWeightsJson(ScoringWeights.toJson(weights));
    profile.setOrganization(organization);
    profile.setCreatedAt(new Date());
    session.save(profile);
    profile.setRootScoringProfileId(profile.getScoringProfileId());
    return profile;
  }

  private Configuration configuration(Organization organization, boolean template) {
    org.immregistries.mismo.match.model.Configuration defaults = new org.immregistries.mismo.match.model.Configuration();
    defaults.setup();
    Configuration configuration = new Configuration();
    configuration.setWorldName("Test");
    configuration.setIslandName(organization.getName());
    configuration.setGeneration(0);
    configuration.setGenerationScore(0);
    configuration.setGeneratedDate(new Date());
    configuration.setHashForSignature(defaults.getHashForSignature());
    configuration.setConfigurationScript(defaults.getConfigurationScript());
    configuration.setContentSha256(Hashes.sha256Hex(defaults.getConfigurationScript()));
    configuration.setOrganization(organization);
    configuration.setTemplate(template);
    configuration.setCreatedAt(new Date());
    session.save(configuration);
    return configuration;
  }

  /** Three scoreable cases and one Research case. */
  private MatchSet matchSet(Organization organization, boolean template) {
    MatchSet matchSet = new MatchSet();
    matchSet.setLabel("Test set " + organization.getName());
    matchSet.setOrganization(organization);
    matchSet.setTemplate(template);
    matchSet.setRootMatchSetId(0);
    matchSet.setCreatedAt(new Date());
    matchSet.setUpdatedAt(new Date());
    session.save(matchSet);
    matchSet.setRootMatchSetId(matchSet.getMatchSetId());
    item(matchSet, "S-1", MatchItem.MATCH);
    item(matchSet, "P-1", MatchItem.POSSIBLE_MATCH);
    item(matchSet, "D-1", MatchItem.NOT_A_MATCH);
    item(matchSet, "R-1", MatchItem.RESEARCH);
    return matchSet;
  }

  private org.immregistries.mismo.trainer.model.MatchItem item(MatchSet matchSet, String label, String expect) {
    org.immregistries.mismo.trainer.model.MatchItem item = new org.immregistries.mismo.trainer.model.MatchItem();
    item.setMatchSet(matchSet);
    item.setLabel(label);
    item.setExpectStatus(expect);
    item.setDataSource("test");
    item.setPatientDataA("nameFirst=Ann,nameLast=Lee,birthDate=20200101,");
    item.setPatientDataB("nameFirst=Ann,nameLast=Lee,birthDate=20200101,");
    item.setCreatedAt(new Date());
    item.setUpdatedAt(new Date());
    session.save(item);
    return item;
  }
}
