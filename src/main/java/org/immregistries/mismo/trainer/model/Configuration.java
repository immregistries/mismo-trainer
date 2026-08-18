package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code Configuration} mapping (see database-schema-migration-plan.md
 * §2.7). This is a persisted record of a generated/submitted weight script;
 * it is unrelated to {@code org.immregistries.mismo.match.model.Configuration},
 * which mismo-match still uses in-memory for actual scoring.
 */
public class Configuration {

  private int configurationId;
  private String worldName;
  private String islandName;
  private int generation;
  private double generationScore;
  private Date generatedDate;
  private String hashForSignature;
  private String configurationScript;
  private Organization organization;
  private boolean isTemplate;
  private User createdByUser;
  private IslandCredential islandCredential;
  private Date createdAt;

  public int getConfigurationId() {
    return configurationId;
  }

  public void setConfigurationId(int configurationId) {
    this.configurationId = configurationId;
  }

  public String getWorldName() {
    return worldName;
  }

  public void setWorldName(String worldName) {
    this.worldName = worldName;
  }

  public String getIslandName() {
    return islandName;
  }

  public void setIslandName(String islandName) {
    this.islandName = islandName;
  }

  public int getGeneration() {
    return generation;
  }

  public void setGeneration(int generation) {
    this.generation = generation;
  }

  public double getGenerationScore() {
    return generationScore;
  }

  public void setGenerationScore(double generationScore) {
    this.generationScore = generationScore;
  }

  public Date getGeneratedDate() {
    return generatedDate;
  }

  public void setGeneratedDate(Date generatedDate) {
    this.generatedDate = generatedDate;
  }

  public String getHashForSignature() {
    return hashForSignature;
  }

  public void setHashForSignature(String hashForSignature) {
    this.hashForSignature = hashForSignature;
  }

  public String getConfigurationScript() {
    return configurationScript;
  }

  public void setConfigurationScript(String configurationScript) {
    this.configurationScript = configurationScript;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  /**
   * Publish switch (database-schema-migration-plan.md §2.10) -- same rule as
   * {@link MatchSet#isTemplate()}.
   */
  public boolean isTemplate() {
    return isTemplate;
  }

  public void setTemplate(boolean isTemplate) {
    this.isTemplate = isTemplate;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public IslandCredential getIslandCredential() {
    return islandCredential;
  }

  public void setIslandCredential(IslandCredential islandCredential) {
    this.islandCredential = islandCredential;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }
}
