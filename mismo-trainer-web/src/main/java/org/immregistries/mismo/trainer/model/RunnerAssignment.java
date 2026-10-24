package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * What a {@link Runner} should use for its next run. Rows are never updated: saving the assignment
 * form inserts {@code revision + 1}, and the current assignment is the highest revision.
 */
public class RunnerAssignment {

  private int runnerAssignmentId;
  private Runner runner;
  private int revision;
  private String name;
  private Configuration configuration;
  private MatchSet matchSet;
  private int populationSize;
  private ScoringProfile scoringProfile;
  private Date createdAt;
  private User createdByUser;

  public int getRunnerAssignmentId() {
    return runnerAssignmentId;
  }

  public void setRunnerAssignmentId(int runnerAssignmentId) {
    this.runnerAssignmentId = runnerAssignmentId;
  }

  public Runner getRunner() {
    return runner;
  }

  public void setRunner(Runner runner) {
    this.runner = runner;
  }

  public int getRevision() {
    return revision;
  }

  public void setRevision(int revision) {
    this.revision = revision;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public MatchSet getMatchSet() {
    return matchSet;
  }

  public void setMatchSet(MatchSet matchSet) {
    this.matchSet = matchSet;
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

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }
}
