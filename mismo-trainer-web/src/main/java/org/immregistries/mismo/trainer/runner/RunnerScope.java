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
  public static final String PURPOSE_PRODUCTION_CALIBRATION = "PRODUCTION_CALIBRATION";
  public static final String PURPOSE_EXPERIMENTAL = "EXPERIMENTAL";
  public static final List<String> PURPOSES = List.of(PURPOSE_MEASUREMENT, PURPOSE_PRODUCTION_CALIBRATION,
      PURPOSE_EXPERIMENTAL);

  /**
   * A runner counts as connected if it was heard from this recently. Running runners report every
   * minute and idle ones send a heartbeat every two (island-runner-implementation-plan.md §2).
   */
  public static final long SEEN_WINDOW_MILLIS = 3L * 60L * 1000L;

  /** Derived, never stored (island-runner-implementation-plan.md §2). */
  public enum State {
    NOT_PAIRED("Not paired"), REVOKED("Revoked"), RUNNING("Running"), IDLE("Idle"), OFFLINE("Offline");

    private final String label;

    State(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  /**
   * Running = has a RUNNING run and was seen within {@link #SEEN_WINDOW_MILLIS}; Idle = seen within
   * that window with no running run; Offline = otherwise.
   *
   * @param active the runner's RUNNING run, or {@code null}
   */
  public static State state(Runner runner, OptimizationRun active, long nowMillis) {
    if (runner.getCredentialHash() == null) {
      return State.NOT_PAIRED;
    }
    if (runner.getCredentialRevokedAt() != null) {
      return State.REVOKED;
    }
    boolean seen = runner.getLastSeenAt() != null
        && nowMillis - runner.getLastSeenAt().getTime() <= SEEN_WINDOW_MILLIS;
    if (!seen) {
      return State.OFFLINE;
    }
    return active != null ? State.RUNNING : State.IDLE;
  }

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

  /**
   * Revokes the runner's credential, e.g. for a lost or retired computer. Its next request is
   * refused; pairing again with a new code gives it a new credential.
   */
  public static void revokeCredential(Runner runner) {
    if (runner.getCredentialHash() != null && runner.getCredentialRevokedAt() == null) {
      runner.setCredentialRevokedAt(new Date());
    }
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

  /** Loads a run only if its runner belongs to {@code user}'s organization. */
  public static OptimizationRun loadRun(Session dataSession, int runId, User user) {
    OptimizationRun run = (OptimizationRun) dataSession.get(OptimizationRun.class, runId);
    if (run == null || !sameOrganization(run.getRunner().getOrganization(), user.getOrganization())) {
      return null;
    }
    return run;
  }

  /**
   * Marks a RUNNING run ABANDONED from the website, e.g. for a computer that went away. This only
   * changes the record: a runner that is actually still going learns of it on its next report
   * ({@code RUN_NOT_ACTIVE}) and keeps optimizing locally.
   *
   * @return {@code false} if the run wasn't running
   */
  public static boolean abandonRun(OptimizationRun run, User user) {
    if (!Protocol.RUN_RUNNING.equals(run.getStatus())) {
      return false;
    }
    String who = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
    String reason = "abandoned on the website by " + who;
    run.setStatus(Protocol.RUN_ABANDONED);
    run.setStopReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
    run.setStoppedAt(new Date());
    return true;
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

  /** Every version of one profile family, newest first, if readable by {@code organization}. */
  @SuppressWarnings("unchecked")
  public static List<ScoringProfile> listProfileVersions(Session dataSession, int rootScoringProfileId,
      Organization organization) {
    Query query = dataSession.createQuery(
        "from ScoringProfile where rootScoringProfileId = ? and (organization = ? or template = true) order by version desc");
    query.setParameter(0, rootScoringProfileId);
    query.setParameter(1, organization);
    return query.list();
  }

  /** Only the owning organization may add versions, template or not. */
  public static boolean isEditable(ScoringProfile profile, User user) {
    return profile != null && sameOrganization(profile.getOrganization(), user.getOrganization());
  }

  /**
   * Saves a Scoring Profile. With {@code previous == null} it starts a new family owned by
   * {@code user}'s organization; otherwise it adds version {@code latest + 1} to {@code previous}'s
   * family, keeping its owner and template flag. Existing rows are never changed, so runs and
   * assignments keep the version they used.
   */
  public static ScoringProfile saveProfile(Session dataSession, ScoringProfile previous, String name, String purpose,
      String description, int[][] weights, User user) {
    ScoringProfile profile = new ScoringProfile();
    profile.setName(name);
    profile.setPurpose(purpose);
    profile.setDescription(description);
    profile.setScoringWeightsJson(org.immregistries.mismo.optimizer.protocol.ScoringWeights.toJson(weights));
    profile.setCreatedAt(new Date());
    profile.setCreatedByUser(user);
    if (previous == null) {
      profile.setOrganization(user.getOrganization());
      profile.setTemplate(false);
      profile.setVersion(1);
      profile.setRootScoringProfileId(0);
      dataSession.save(profile);
      profile.setRootScoringProfileId(profile.getScoringProfileId());
    } else {
      List<ScoringProfile> versions = listProfileVersions(dataSession, previous.getRootScoringProfileId(),
          user.getOrganization());
      profile.setOrganization(previous.getOrganization());
      profile.setTemplate(previous.isTemplate());
      profile.setRootScoringProfileId(previous.getRootScoringProfileId());
      profile.setVersion(versions.get(0).getVersion() + 1);
      dataSession.save(profile);
    }
    return profile;
  }

  /** "Selected by default" on the assignment form: the first measurement profile, if any. */
  public static ScoringProfile defaultScoringProfile(Session dataSession, Organization organization) {
    List<ScoringProfile> profiles = listScoringProfiles(dataSession, organization);
    return profiles.isEmpty() ? null : profiles.get(0);
  }
}
