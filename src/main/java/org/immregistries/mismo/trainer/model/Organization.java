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
