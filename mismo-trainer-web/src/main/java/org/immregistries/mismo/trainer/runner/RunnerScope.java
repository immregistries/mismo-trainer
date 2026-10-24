package org.immregistries.mismo.trainer.runner;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.OptimizationRun;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.Runner;
import org.immregistries.mismo.trainer.model.RunnerAssignment;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.User;

/**
 * Organization-scoped reads and writes for Runners, their assignments and runs, and Scoring
 * Profiles -- the Runner counterpart of {@code OrgScope}. Runners, assignments and runs are never
 * templates: only the owning organization sees them. Scoring Profiles follow the template rule
 * (owned or template). Caller owns the transaction for every write.
 */
public final class RunnerScope {

  public static final String PURPOSE_MEASUREMENT = "MEASUREMENT";

  private RunnerScope() {
  }

  private static boolean sameOrganization(Organization a, Organization b) {
    return a != null && b != null && a.getOrganizationId() == b.getOrganizationId();
  }

  /** A newly created runner and its pairing code, which is shown once and never stored. */
  public record CreatedRunner(Runner runner, String pairingCode) {
  }

  public static CreatedRunner createRunner(Session dataSession, String displayName, User user) {
    Runner runner = new Runner();
    runner.setOrganization(user.getOrganization());
    runner.setDisplayName(displayName);
    runner.setCreatedAt(new Date());
    runner.setCreatedByUser(user);
    String pairingCode = setNewPairingCode(runner);
    dataSession.save(runner);
    return new CreatedRunner(runner, pairingCode);
  }

  /**
   * Gives {@code runner} a new pairing code valid for 15 minutes. An existing credential keeps
   * working until the new pairing succeeds (island-runner-implementation-plan.md §2).
   *
   * @return the raw code, to show the user once
   */
  public static String setNewPairingCode(Runner runner) {
    String pairingCode = RunnerCredentialSupport.generatePairingCode();
    runner.setPairingCodeHash(RunnerCredentialSupport.hash(pairingCode));
    runner.setPairingExpiresAt(new Date(System.currentTimeMillis() + RunnerCredentialSupport.PAIRING_VALID_MILLIS));
    return pairingCode;
  }

  /** Loads a runner only if it belongs to {@code user}'s organization. */
  public static Runner loadRunner(Session dataSession, int runnerId, User user) {
    Runner runner = (Runner) dataSession.get(Runner.class, runnerId);
    if (runner == null || !sameOrganization(runner.getOrganization(), user.getOrganization())) {
      return null;
    }
    return runner;
  }

  @SuppressWarnings("unchecked")
  public static List<Runner> listRunners(Session dataSession, User user) {
    Query query = dataSession.createQuery("from Runner where organization = ? order by displayName");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /** @return the highest revision, or {@code null} if the runner has never been assigned anything */
  public static RunnerAssignment currentAssignment(Session dataSession, Runner runner) {
    Query query = dataSession.createQuery("from RunnerAssignment where runner = ? order by revision desc");
    query.setParameter(0, runner);
    query.setMaxResults(1);
    return (RunnerAssignment) query.uniqueResult();
  }

  @SuppressWarnings("unchecked")
  public static List<RunnerAssignment> listAssignments(Session dataSession, Runner runner) {
    Query query = dataSession.createQuery("from RunnerAssignment where runner = ? order by revision desc");
    query.setParameter(0, runner);
    return query.list();
  }

  /**
   * Saves the assignment form as a new revision; existing rows are never changed. The caller has
   * already checked that the configuration, Test Set and profile are readable by the runner's
   * organization.
   */
  public static RunnerAssignment saveAssignment(Session dataSession, Runner runner, String name,
      Configuration configuration, MatchSet matchSet, int populationSize, ScoringProfile scoringProfile,
      User user) {
    RunnerAssignment previous = currentAssignment(dataSession, runner);
    RunnerAssignment assignment = new RunnerAssignment();
    assignment.setRunner(runner);
    assignment.setRevision(previous == null ? 1 : previous.getRevision() + 1);
    assignment.setName(name);
    assignment.setConfiguration(configuration);
    assignment.setMatchSet(matchSet);
    assignment.setPopulationSize(populationSize);
    assignment.setScoringProfile(scoringProfile);
    assignment.setCreatedAt(new Date());
    assignment.setCreatedByUser(user);
    dataSession.save(assignment);
    return assignment;
  }

  /** The runner's run still marked {@code RUNNING}, or {@code null}. */
  public static OptimizationRun activeRun(Session dataSession, Runner runner) {
    Query query = dataSession.createQuery(
        "from OptimizationRun where runner = ? and status = ? order by startedAt desc");
    query.setParameter(0, runner);
    query.setParameter(1, Protocol.RUN_RUNNING);
    query.setMaxResults(1);
    return (OptimizationRun) query.uniqueResult();
  }

  @SuppressWarnings("unchecked")
  public static List<OptimizationRun> listRuns(Session dataSession, Runner runner) {
    Query query = dataSession.createQuery("from OptimizationRun where runner = ? order by startedAt desc");
    query.setParameter(0, runner);
    return query.list();
  }

  /** The most recent run of each runner in {@code runners}, keyed by runner id. */
  public static Map<Integer, OptimizationRun> latestRuns(Session dataSession, List<Runner> runners) {
    Map<Integer, OptimizationRun> latest = new LinkedHashMap<Integer, OptimizationRun>();
    for (Runner runner : runners) {
      Query query = dataSession.createQuery("from OptimizationRun where runner = ? order by startedAt desc");
      query.setParameter(0, runner);
      query.setMaxResults(1);
      OptimizationRun run = (OptimizationRun) query.uniqueResult();
      if (run != null) {
        latest.put(runner.getRunnerId(), run);
      }
    }
    return latest;
  }

  /** Candidates a run uploaded, newest first. */
  @SuppressWarnings("unchecked")
  public static List<Configuration> listCandidates(Session dataSession, OptimizationRun run) {
    Query query = dataSession.createQuery("from Configuration where optimizationRun = ? order by configurationId desc");
    query.setParameter(0, run);
    return query.list();
  }

  /**
   * Scoring Profiles readable by {@code organization} (owned or template), newest version of each
   * family only, measurement profiles first.
   */
  @SuppressWarnings("unchecked")
  public static List<ScoringProfile> listScoringProfiles(Session dataSession, Organization organization) {
    Query query = dataSession.createQuery(
        "from ScoringProfile where (organization = ? or template = true) order by rootScoringProfileId, version desc");
    query.setParameter(0, organization);
    List<ScoringProfile> latest = new ArrayList<ScoringProfile>();
    int lastRoot = -1;
    for (ScoringProfile profile : (List<ScoringProfile>) query.list()) {
      if (profile.getRootScoringProfileId() != lastRoot) {
        latest.add(profile);
        lastRoot = profile.getRootScoringProfileId();
      }
    }
    latest.sort((a, b) -> Boolean.compare(!PURPOSE_MEASUREMENT.equals(a.getPurpose()),
        !PURPOSE_MEASUREMENT.equals(b.getPurpose())));
    return latest;
  }

  public static ScoringProfile loadScoringProfile(Session dataSession, int scoringProfileId,
      Organization organization) {
    ScoringProfile profile = (ScoringProfile) dataSession.get(ScoringProfile.class, scoringProfileId);
    if (profile == null || !(profile.isTemplate() || sameOrganization(profile.getOrganization(), organization))) {
      return null;
    }
    return profile;
  }

  /** "Selected by default" on the assignment form: the first measurement profile, if any. */
  public static ScoringProfile defaultScoringProfile(Session dataSession, Organization organization) {
    List<ScoringProfile> profiles = listScoringProfiles(dataSession, organization);
    return profiles.isEmpty() ? null : profiles.get(0);
  }
}
