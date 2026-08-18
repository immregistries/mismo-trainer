package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code MatchSet} mapping (see database-schema-migration-plan.md §2.7).
 */
public class MatchSet {

  private int matchSetId;
  private String label;
  private Organization organization;
  private boolean isTemplate;
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

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  /**
   * Publish switch (database-schema-migration-plan.md §2.10): {@code true} means every
   * organization may read/browse/score against this row, but only its owning organization may
   * ever edit it. May only be set {@code true} when the owning organization's
   * {@link Organization#isTemplateOrg()} is {@code true} -- enforced in
   * {@link org.immregistries.mismo.trainer.servlet.OrgScope#requireTemplateEligible}, not by a
   * database constraint.
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

  @Override
  public boolean equals(Object o) {
    if (o instanceof MatchSet && getMatchSetId() > 0) {
      return ((MatchSet) o).getMatchSetId() == getMatchSetId();
    }
    return super.equals(o);
  }
}
