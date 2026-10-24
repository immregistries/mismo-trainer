package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A collection of raw {@code (signature, count)} pairs uploaded for batch analysis
 * (v2-roadmap.md §12; database-changes-for-functional-model.md §7). Deliberately holds only the
 * raw uploaded pairs -- see {@link SignatureBatchEntry} -- never a decoded classification or which
 * {@link Configuration} was used, so the batch stays re-analyzable against any configuration later
 * rather than going stale the moment a different one becomes relevant.
 */
public class SignatureBatch {

  private int signatureBatchId;
  private Organization organization;
  private String label;
  private User uploadedByUser;
  private Date uploadedAt;

  public int getSignatureBatchId() {
    return signatureBatchId;
  }

  public void setSignatureBatchId(int signatureBatchId) {
    this.signatureBatchId = signatureBatchId;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  /** Nullable -- who uploaded this batch, if known. */
  public User getUploadedByUser() {
    return uploadedByUser;
  }

  public void setUploadedByUser(User uploadedByUser) {
    this.uploadedByUser = uploadedByUser;
  }

  public Date getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(Date uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
