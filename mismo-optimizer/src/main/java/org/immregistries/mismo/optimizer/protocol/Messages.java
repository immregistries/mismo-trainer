package org.immregistries.mismo.optimizer.protocol;

import java.util.List;
import java.util.Map;

/**
 * Request and response bodies of the Runner API (docs/centrally-managed-local-island-runner.md
 * §10, docs/island-runner-implementation-plan.md §4). Both sides serialize these with
 * {@link Protocol#gson()}. Fields that may be absent are boxed types.
 */
public final class Messages {

  private Messages() {
  }

  /** {@code POST /runner-pairings} */
  public record PairingRequest(String pairingCode, String clientVersion, String protocolVersion,
      String runtimeInfo) {
  }

  public record PairingResponse(int runnerId, String runnerName, String organizationName, String credential) {
  }

  /** {@code GET /runners/me} and {@code POST /runners/me/heartbeats} */
  public record RunnerIdentity(int runnerId, String runnerName, String organizationName) {
  }

  /** {@code GET /runners/me/assignment} */
  public record Assignment(int assignmentId, int revision, String name, ConfigurationRef configuration,
      TestSetRef testSet, int populationSize, ScoringProfileInfo scoringProfile) {
  }

  /** The seed configuration; {@code url} serves its exact script, whose SHA-256 is {@code sha256}. */
  public record ConfigurationRef(int id, String label, String sha256, String url) {
  }

  /** A Test Set snapshot; {@code url} serves {@link TestSetContent} JSON whose SHA-256 is {@code sha256}. */
  public record TestSetRef(int matchSetId, String label, String lifecycleStatus, int snapshotId, String sha256,
      int caseCount, String url) {
  }

  /** {@code weights} uses the {@code scoringWeights} key names of a configuration script. */
  public record ScoringProfileInfo(int id, int version, String name, String purpose, Map<String, Integer> weights) {
  }

  /** {@code GET /test-set-snapshots/{id}/content} */
  public record TestSetContent(int formatVersion, int matchSetId, String label, List<TestCase> cases) {
  }

  /** The stored strings of one case, as-is; see {@code TestCaseLoader.toRuntimeMatchItem}. */
  public record TestCase(String label, String description, String expectStatus, String patientDataA,
      String patientDataB) {
  }

  /** {@code POST /runs} */
  public record CreateRunRequest(int assignmentId, int revision, String configurationSha256, int testSetSnapshotId,
      String testSetSha256) {
  }

  /** {@code POST /runs}, {@code GET /runs/{id}}, {@code POST /runs/{id}/stop} */
  public record RunInfo(int runId, String status, int assignmentId, int revision, Integer lastGeneration,
      Double bestScore, Integer bestConfigurationId) {
  }

  /** {@code POST /runs/{id}/progress} */
  public record ProgressReport(int generation, double candidateRate, double bestScore) {
  }

  /** {@code POST /runs/{id}/candidates} */
  public record CandidateUpload(int generation, double score, String configurationScript) {
  }

  /** {@code created} is false when the same content was already stored for this run. */
  public record CandidateReceipt(int configurationId, String contentSha256, boolean created) {
  }

  /** {@code POST /runs/{id}/stop} and {@code POST /runs/{id}/fail} */
  public record StopRequest(String reason) {
  }

  /** Body of every non-2xx response. {@code error} is an {@link ErrorCode} name. */
  public record ErrorResponse(String error, String message) {
  }
}
