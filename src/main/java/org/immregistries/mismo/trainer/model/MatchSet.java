package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code MatchSet} mapping (see database-schema-migration-plan.md §2.7).
 * {@code updateDate} is the legacy v1 timestamp column, retained until the
 * Phase 7 cleanup drops it in favor of {@code createdAt}/{@code updatedAt}.
 */
public class MatchSet {

  private int matchSetId;
  private String label;
  private Date updateDate;
  private Organization organization;
  private User createdByUser;
  private User updatedByUser;
  private Date createdAt;
  private Date updatedAt;

  public int getMatchSetId() {
    return matchSetId;
  }

  public void setMatchSetId(int matchSetId) {
    this.matchSetId = matchSetId;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Date getUpdateDate() {
    return updateDate;
  }

  public void setUpdateDate(Date updateDate) {
    this.updateDate = updateDate;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
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
