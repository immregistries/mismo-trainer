package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code MatchSet} mapping (see database-schema-migration-plan.md §2.7).
 */
public class MatchSet {

  /** {@link #getLifecycleStatus()} value: newly created or still being edited. */
  public static final String LIFECYCLE_DRAFT = "DRAFT";
  /** {@link #getLifecycleStatus()} value: has been reviewed by an analyst. */
  public static final String LIFECYCLE_REVIEWED = "REVIEWED";
  /** {@link #getLifecycleStatus()} value: signed off; case-level edits are rejected. */
  public static final String LIFECYCLE_APPROVED = "APPROVED";

  private int matchSetId;
  private String label;
  private Organization organization;
  private boolean isTemplate;
  private User createdByUser;
  private User updatedByUser;
  private Date createdAt;
  private Date updatedAt;
  private String lifecycleStatus = LIFECYCLE_DRAFT;
  private String version;
  private Integer copiedFromMatchSetId;
  private int rootMatchSetId;
  private Date archivedAt;

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

  /** DRAFT/REVIEWED/APPROVED (see the {@code LIFECYCLE_*} constants) -- reversible either way. */
  public String getLifecycleStatus() {
    return lifecycleStatus;
  }

  public void setLifecycleStatus(String lifecycleStatus) {
    this.lifecycleStatus = lifecycleStatus;
  }

  /** Free-form version label (e.g. "v1.2"), distinct from {@link #getLabel()}. */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * The match_set this row was deep-copied from, or {@code null} if it was created directly
   * (not by copying). Informational lineage only -- never a live dependency.
   */
  public Integer getCopiedFromMatchSetId() {
    return copiedFromMatchSetId;
  }

  public void setCopiedFromMatchSetId(Integer copiedFromMatchSetId) {
    this.copiedFromMatchSetId = copiedFromMatchSetId;
  }

  /**
   * The id of the root of this row's version family: its own id if this row was created
   * directly, or the id inherited from its source (not the source's own id) if it was copied.
   * Always populated -- "every version of this Test Set" is {@code WHERE rootMatchSetId = ?}.
   */
  public int getRootMatchSetId() {
    return rootMatchSetId;
  }

  public void setRootMatchSetId(int rootMatchSetId) {
    this.rootMatchSetId = rootMatchSetId;
  }

  /** Non-null when archived; independent of {@link #getLifecycleStatus()}. */
  public Date getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Date archivedAt) {
    this.archivedAt = archivedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof MatchSet && getMatchSetId() > 0) {
      return ((MatchSet) o).getMatchSetId() == getMatchSetId();
    }
    return super.equals(o);
  }
}
