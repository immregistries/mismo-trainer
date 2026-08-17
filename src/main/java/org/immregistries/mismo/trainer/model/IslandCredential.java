package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A machine credential an Island optimization process uses to authenticate
 * to the central Trainer, in place of an InteropHub user session. The raw
 * credential is only ever shown at creation time; only its hash is stored.
 */
public class IslandCredential {

  private int islandCredentialId;
  private Organization organization;
  private String name;
  private String credentialHash;
  private User createdByUser;
  private Date createdAt;
  private Date lastUsedAt;
  private Date revokedAt;

  public int getIslandCredentialId() {
    return islandCredentialId;
  }

  public void setIslandCredentialId(int islandCredentialId) {
    this.islandCredentialId = islandCredentialId;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCredentialHash() {
    return credentialHash;
  }

  public void setCredentialHash(String credentialHash) {
    this.credentialHash = credentialHash;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public Date getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Date lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public Date getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Date revokedAt) {
    this.revokedAt = revokedAt;
  }
}
