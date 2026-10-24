package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * One historical reviewer opinion on a {@link MatchItem} (database-changes-for-functional-
 * model.md §4). Append-only: rows are only ever inserted, never updated or deleted. Inserting a
 * row is what updates {@code MatchItem.expectStatus} and sets {@code MatchItem.isReviewed = true};
 * the row itself is immutable history from then on.
 */
public class MatchItemReview {

  private int matchItemReviewId;
  private MatchItem matchItem;
  private User reviewerUser;
  private String classification;
  private String notes;
  private Date reviewedAt;

  public int getMatchItemReviewId() {
    return matchItemReviewId;
  }

  public void setMatchItemReviewId(int matchItemReviewId) {
    this.matchItemReviewId = matchItemReviewId;
  }

  public MatchItem getMatchItem() {
    return matchItem;
  }

  public void setMatchItem(MatchItem matchItem) {
    this.matchItem = matchItem;
  }

  public User getReviewerUser() {
    return reviewerUser;
  }

  public void setReviewerUser(User reviewerUser) {
    this.reviewerUser = reviewerUser;
  }

  /** Match / Possible Match / Not a Match / Research / Not Sure -- one of the classify actions. */
  public String getClassification() {
    return classification;
  }

  public void setClassification(String classification) {
    this.classification = classification;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Date getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Date reviewedAt) {
    this.reviewedAt = reviewedAt;
  }
}
