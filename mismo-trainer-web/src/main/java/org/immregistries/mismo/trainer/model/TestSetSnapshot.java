package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * An immutable copy of one {@link MatchSet}'s scoreable cases, stored as the exact JSON bytes
 * served to runners (docs/island-runner-implementation-plan.md D3).
 */
public class TestSetSnapshot {

  private int testSetSnapshotId;
  private MatchSet matchSet;
  private String contentSha256;
  private int caseCount;
  private String lifecycleStatus;
  private String contentJson;
  private Date createdAt;

  public int getTestSetSnapshotId() {
    return testSetSnapshotId;
  }

  public void setTestSetSnapshotId(int testSetSnapshotId) {
    this.testSetSnapshotId = testSetSnapshotId;
  }

  public MatchSet getMatchSet() {
    return matchSet;
  }

  public void setMatchSet(MatchSet matchSet) {
    this.matchSet = matchSet;
  }

  public String getContentSha256() {
    return contentSha256;
  }

  public void setContentSha256(String contentSha256) {
    this.contentSha256 = contentSha256;
  }

  public int getCaseCount() {
    return caseCount;
  }

  public void setCaseCount(int caseCount) {
    this.caseCount = caseCount;
  }

  public String getLifecycleStatus() {
    return lifecycleStatus;
  }

  public void setLifecycleStatus(String lifecycleStatus) {
    this.lifecycleStatus = lifecycleStatus;
  }

  public String getContentJson() {
    return contentJson;
  }

  public void setContentJson(String contentJson) {
    this.contentJson = contentJson;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }
}
