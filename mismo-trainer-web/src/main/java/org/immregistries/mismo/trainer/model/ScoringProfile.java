package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A named, versioned fitness function for optimization runs
 * (docs/optimization-objectives-and-scoring-profiles.md; docs/island-runner-implementation-plan.md D6).
 * Rows are never updated: an edit inserts {@code version + 1} with the same {@code rootScoringProfileId}.
 */
public class ScoringProfile {

  private int scoringProfileId;
  private int rootScoringProfileId;
  private int version;
  private String name;
  private String purpose;
  private String description;
  private String scoringWeightsJson;
  private Organization organization;
  private boolean template;
  private Date createdAt;
  private User createdByUser;

  public int getScoringProfileId() {
    return scoringProfileId;
  }

  public void setScoringProfileId(int scoringProfileId) {
    this.scoringProfileId = scoringProfileId;
  }

  public int getRootScoringProfileId() {
    return rootScoringProfileId;
  }

  public void setRootScoringProfileId(int rootScoringProfileId) {
    this.rootScoringProfileId = rootScoringProfileId;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getScoringWeightsJson() {
    return scoringWeightsJson;
  }

  public void setScoringWeightsJson(String scoringWeightsJson) {
    this.scoringWeightsJson = scoringWeightsJson;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public boolean isTemplate() {
    return template;
  }

  public void setTemplate(boolean template) {
    this.template = template;
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
