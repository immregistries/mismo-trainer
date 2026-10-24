package org.immregistries.mismo.trainer;

import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.TestCaseLoader;

/**
 * Converts persisted trainer {@code MatchItem} rows to the mismo-match runtime {@code MatchItem}
 * that {@code PatientCompare}/{@code Scorer} use. The string-field conversion (and its null
 * defaulting) lives in {@link TestCaseLoader} so the Island Runner shares it; this class adds the
 * webapp-only fields that come from Hibernate relations.
 */
public final class RuntimeMatchItems {

  private RuntimeMatchItems() {
  }

  /**
   * <p>{@code matchSet} is populated from the trainer entity's parent: the runtime class defaults
   * it to {@code null}, which used to make {@code ReviewServlet}'s
   * {@code matchItem.getMatchSet().getMatchSetId()} NPE on every row.
   *
   * @param matchItem
   *          the Hibernate-loaded trainer entity
   * @return an equivalent mismo-match runtime object, safe to hand to PatientCompare/Scorer
   */
  public static MatchItem toRuntimeMatchItem(org.immregistries.mismo.trainer.model.MatchItem matchItem) {
    MatchItem runtimeMatchItem = TestCaseLoader.toRuntimeMatchItem(matchItem.getLabel(),
        matchItem.getDescription(), matchItem.getExpectStatus(), matchItem.getPatientDataA(),
        matchItem.getPatientDataB());
    runtimeMatchItem.setMatchItemId(matchItem.getMatchItemId());
    runtimeMatchItem.setUpdateDate(matchItem.getUpdatedAt());
    if (matchItem.getMatchSet() != null) {
      org.immregistries.mismo.match.model.MatchSet runtimeMatchSet = new org.immregistries.mismo.match.model.MatchSet();
      runtimeMatchSet.setMatchSetId(matchItem.getMatchSet().getMatchSetId());
      String label = matchItem.getMatchSet().getLabel();
      runtimeMatchSet.setLabel(label == null ? "" : label);
      runtimeMatchItem.setMatchSet(runtimeMatchSet);
    }
    if (matchItem.getUpdatedByUser() != null) {
      org.immregistries.mismo.match.model.User runtimeUser = new org.immregistries.mismo.match.model.User();
      runtimeUser.setUserId(matchItem.getUpdatedByUser().getUserId());
      runtimeUser.setName(matchItem.getUpdatedByUser().getDisplayName() != null
          ? matchItem.getUpdatedByUser().getDisplayName()
          : matchItem.getUpdatedByUser().getEmail());
      runtimeUser.setEmail(matchItem.getUpdatedByUser().getEmail());
      runtimeMatchItem.setUser(runtimeUser);
    }
    return runtimeMatchItem;
  }
}
