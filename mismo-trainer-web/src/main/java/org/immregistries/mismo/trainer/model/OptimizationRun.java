package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * One execution of one {@link RunnerAssignment} revision, with every input frozen
 * (docs/centrally-managed-local-island-runner.md §4.3, §5).
 */
public class OptimizationRun {

  private int optimizationRunId;
  private Runner runner;
  private RunnerAssignment runnerAssignment;
  private Configuration seedConfiguration;
  private String seedConfigurationSha256;
  private TestSetSnapshot testSetSnapshot;
  private int populationSize;
  private ScoringProfile scoringProfile;
  private String scoringWeightsJson;
  private String clientVersion;
  private String protocolVersion;
  private String status;
  private String stopReason;
  private Date startedAt;
  private Date stoppedAt;
  private Date lastReportedAt;
  private Integer lastGeneration;
  private Double candidateRate;
  private Double bestScore;
  private Configuration bestConfiguration;

  public int getOptimizationRunId() {
    return optimizationRunId;
  }

  public void setOptimizationRunId(int optimizationRunId) {
    this.optimizationRunId = optimizationRunId;
  }

  public Runner getRunner() {
    return runner;
  }

  public void setRunner(Runner runner) {
    this.runner = runner;
  }

  public RunnerAssignment getRunnerAssignment() {
    return runnerAssignment;
  }

  public void setRunnerAssignment(RunnerAssignment runnerAssignment) {
    this.runnerAssignment = runnerAssignment;
  }

  public Configuration getSeedConfiguration() {
    return seedConfiguration;
  }

  public void setSeedConfiguration(Configuration seedConfiguration) {
    this.seedConfiguration = seedConfiguration;
  }

  public String getSeedConfigurationSha256() {
    return seedConfigurationSha256;
  }

  public void setSeedConfigurationSha256(String seedConfigurationSha256) {
    this.seedConfigurationSha256 = seedConfigurationSha256;
  }

  public TestSetSnapshot getTestSetSnapshot() {
    return testSetSnapshot;
  }

  public void setTestSetSnapshot(TestSetSnapshot testSetSnapshot) {
    this.testSetSnapshot = testSetSnapshot;
  }

  public int getPopulationSize() {
    return populationSize;
  }

  public void setPopulationSize(int populationSize) {
    this.populationSize = populationSize;
  }

  public ScoringProfile getScoringProfile() {
    return scoringProfile;
  }

  public void setScoringProfile(ScoringProfile scoringProfile) {
    this.scoringProfile = scoringProfile;
  }

  public String getScoringWeightsJson() {
    return scoringWeightsJson;
  }

  public void setScoringWeightsJson(String scoringWeightsJson) {
    this.scoringWeightsJson = scoringWeightsJson;
  }

  public String getClientVersion() {
    return clientVersion;
  }

  public void setClientVersion(String clientVersion) {
    this.clientVersion = clientVersion;
  }

  public String getProtocolVersion() {
    return protocolVersion;
  }

  public void setProtocolVersion(String protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStopReason() {
    return stopReason;
  }

  public void setStopReason(String stopReason) {
    this.stopReason = stopReason;
  }

  public Date getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Date startedAt) {
    this.startedAt = startedAt;
  }

  public Date getStoppedAt() {
    return stoppedAt;
  }

  public void setStoppedAt(Date stoppedAt) {
    this.stoppedAt = stoppedAt;
  }

  public Date getLastReportedAt() {
    return lastReportedAt;
  }

  public void setLastReportedAt(Date lastReportedAt) {
    this.lastReportedAt = lastReportedAt;
  }

  public Integer getLastGeneration() {
    return lastGeneration;
  }

  public void setLastGeneration(Integer lastGeneration) {
    this.lastGeneration = lastGeneration;
  }

  public Double getCandidateRate() {
    return candidateRate;
  }

  public void setCandidateRate(Double candidateRate) {
    this.candidateRate = candidateRate;
  }

  public Double getBestScore() {
    return bestScore;
  }

  public void setBestScore(Double bestScore) {
    this.bestScore = bestScore;
  }

  public Configuration getBestConfiguration() {
    return bestConfiguration;
  }

  public void setBestConfiguration(Configuration bestConfiguration) {
    this.bestConfiguration = bestConfiguration;
  }
}
