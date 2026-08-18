package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code MatchItem} mapping (see database-schema-migration-plan.md §2.7).
 */
public class MatchItem {

  private int matchItemId;
  private MatchSet matchSet;
  private String label;
  private String description;
  private String patientDataA;
  private String patientDataB;
  private String expectStatus;
  private String dataSource;
  private User createdByUser;
  private User updatedByUser;
  private Date createdAt;
  private Date updatedAt;

  public int getMatchItemId() {
    return matchItemId;
  }

  public void setMatchItemId(int matchItemId) {
    this.matchItemId = matchItemId;
  }

  public MatchSet getMatchSet() {
    return matchSet;
  }

  public void setMatchSet(MatchSet matchSet) {
    this.matchSet = matchSet;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPatientDataA() {
    return patientDataA;
  }

  public void setPatientDataA(String patientDataA) {
    this.patientDataA = patientDataA;
  }

  public String getPatientDataB() {
    return patientDataB;
  }

  public void setPatientDataB(String patientDataB) {
    this.patientDataB = patientDataB;
  }

  public String getExpectStatus() {
    return expectStatus;
  }

  public void setExpectStatus(String expectStatus) {
    this.expectStatus = expectStatus;
  }

  public String getDataSource() {
    return dataSource;
  }

  public void setDataSource(String dataSource) {
    this.dataSource = dataSource;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public User getUpdatedByUser() {
    return updatedByUser;
  }

  public void setUpdatedByUser(User updatedByUser) {
    this.updatedByUser = updatedByUser;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt;
  }
}
