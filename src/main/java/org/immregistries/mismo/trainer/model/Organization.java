package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A Mismo-Trainer tenant. Every organization-owned record (user, match set,
 * configuration, island credential) belongs to exactly one organization.
 */
public class Organization {

  private int organizationId;
  private String name;
  private String domain;
  private boolean isTemplateOrg;
  private Date createdAt;
  private Date updatedAt;

  public int getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(int organizationId) {
    this.organizationId = organizationId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  /**
   * Marks this organization's {@code match_set}/{@code configuration} rows as eligible to be
   * published as templates (database-schema-migration-plan.md §2.10). {@code true} for AIRA
   * only; a row's own {@code isTemplate} flag is the actual publish switch.
   */
  public boolean isTemplateOrg() {
    return isTemplateOrg;
  }

  public void setTemplateOrg(boolean isTemplateOrg) {
    this.isTemplateOrg = isTemplateOrg;
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
