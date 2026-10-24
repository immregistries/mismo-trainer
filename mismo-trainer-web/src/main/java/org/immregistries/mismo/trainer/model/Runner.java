package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * One installed copy of the Mismo Runner on one computer (docs/centrally-managed-local-island-runner.md
 * §4.1). Only hashes of the pairing code and credential are stored; see {@code RunnerCredentialSupport}.
 */
public class Runner {

  private int runnerId;
  private Organization organization;
  private String displayName;
  private String pairingCodeHash;
  private Date pairingExpiresAt;
  private String credentialHash;
  private Date credentialCreatedAt;
  private Date credentialLastUsedAt;
  private Date credentialRevokedAt;
  private String clientVersion;
  private String protocolVersion;
  private String runtimeInfo;
  private Date lastSeenAt;
  private Date createdAt;
  private User createdByUser;

  public int getRunnerId() {
    return runnerId;
  }

  public void setRunnerId(int runnerId) {
    this.runnerId = runnerId;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPairingCodeHash() {
    return pairingCodeHash;
  }

  public void setPairingCodeHash(String pairingCodeHash) {
    this.pairingCodeHash = pairingCodeHash;
  }

  public Date getPairingExpiresAt() {
    return pairingExpiresAt;
  }

  public void setPairingExpiresAt(Date pairingExpiresAt) {
    this.pairingExpiresAt = pairingExpiresAt;
  }

  public String getCredentialHash() {
    return credentialHash;
  }

  public void setCredentialHash(String credentialHash) {
    this.credentialHash = credentialHash;
  }

  public Date getCredentialCreatedAt() {
    return credentialCreatedAt;
  }

  public void setCredentialCreatedAt(Date credentialCreatedAt) {
    this.credentialCreatedAt = credentialCreatedAt;
  }

  public Date getCredentialLastUsedAt() {
    return credentialLastUsedAt;
  }

  public void setCredentialLastUsedAt(Date credentialLastUsedAt) {
    this.credentialLastUsedAt = credentialLastUsedAt;
  }

  public Date getCredentialRevokedAt() {
    return credentialRevokedAt;
  }

  public void setCredentialRevokedAt(Date credentialRevokedAt) {
    this.credentialRevokedAt = credentialRevokedAt;
  }

  public String getClientVersion() {
    return clientVersion;
  }

  public void setClientVersion(String clientVersion) {
    this.clientVersion = clientVersion;
  }

  public String getProtocolVersion() {
    return protocolVersion;
  }

  public void setProtocolVersion(String protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  public String getRuntimeInfo() {
    return runtimeInfo;
  }

  public void setRuntimeInfo(String runtimeInfo) {
    this.runtimeInfo = runtimeInfo;
  }

  public Date getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Date lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
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
