package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned persistence replacement for the mismo-match-jar
 * {@code MatchItem} mapping (see database-schema-migration-plan.md §2.7).
 */
public class MatchItem {

  /** {@link #getProvenanceType()} value: entered by hand (the default). */
  public static final String PROVENANCE_MANUAL = "MANUAL";
  /** {@link #getProvenanceType()} value: brought in via a Test Set file upload. */
  public static final String PROVENANCE_IMPORTED = "IMPORTED";
  /** {@link #getProvenanceType()} value: produced by a random/example generator. */
  public static final String PROVENANCE_GENERATED = "GENERATED";
  /** {@link #getProvenanceType()} value: synthesized from a selected signature. */
  public static final String PROVENANCE_SIGNATURE_GENERATED = "SIGNATURE_GENERATED";
  /** {@link #getProvenanceType()} value: produced by a Test Set deep-copy. */
  public static final String PROVENANCE_COPIED = "COPIED";

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
  private String originalExpectStatus;
  private boolean isReviewed;
  private boolean needsReview;
  private String provenanceType = PROVENANCE_MANUAL;
  private Integer copiedFromMatchItemId;
  private String sourceSignature;
  private String reviewNotes;

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

  /**
   * The {@link #getExpectStatus()} this row had at creation -- snapshotted once and never
   * modified again. For a copy, this is the source's expectation <em>at copy time</em>, not the
   * source's own original.
   */
  public String getOriginalExpectStatus() {
    return originalExpectStatus;
  }

  public void setOriginalExpectStatus(String originalExpectStatus) {
    this.originalExpectStatus = originalExpectStatus;
  }

  /**
   * Set {@code true} by the classify action itself (never inferred by comparing
   * {@link #getExpectStatus()} to {@link #getOriginalExpectStatus()}) -- confirming an
   * already-correct classification still counts as reviewed. Logically equivalent to "at least
   * one {@code MatchItemReview} row exists for this case," maintained as a denormalized flag
   * since it's filtered on frequently.
   */
  public boolean isReviewed() {
    return isReviewed;
  }

  public void setReviewed(boolean isReviewed) {
    this.isReviewed = isReviewed;
  }

  /** "Needs further review" flag -- fully independent of {@link #isReviewed()}. */
  public boolean isNeedsReview() {
    return needsReview;
  }

  public void setNeedsReview(boolean needsReview) {
    this.needsReview = needsReview;
  }

  /** MANUAL/IMPORTED/GENERATED/SIGNATURE_GENERATED/COPIED (see the {@code PROVENANCE_*} constants). */
  public String getProvenanceType() {
    return provenanceType;
  }

  public void setProvenanceType(String provenanceType) {
    this.provenanceType = provenanceType;
  }

  /**
   * The match_item this row was deep-copied from, or {@code null} if it wasn't. Its full review
   * history stays reachable through this pointer even though it isn't duplicated onto the copy.
   */
  public Integer getCopiedFromMatchItemId() {
    return copiedFromMatchItemId;
  }

  public void setCopiedFromMatchItemId(Integer copiedFromMatchItemId) {
    this.copiedFromMatchItemId = copiedFromMatchItemId;
  }

  /** Populated only when {@link #getProvenanceType()} is {@link #PROVENANCE_SIGNATURE_GENERATED}. */
  public String getSourceSignature() {
    return sourceSignature;
  }

  public void setSourceSignature(String sourceSignature) {
    this.sourceSignature = sourceSignature;
  }

  /** Free-text, editable review commentary -- distinct from {@link #getDescription()}. */
  public String getReviewNotes() {
    return reviewNotes;
  }

  public void setReviewNotes(String reviewNotes) {
    this.reviewNotes = reviewNotes;
  }
}
