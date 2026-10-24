package org.immregistries.mismo.trainer.servlet;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;

/**
 * Derives a Match/Possible Match/Not a Match classification from a signature alone -- something
 * {@code PatientCompare.getResult()} cannot do, since it calls {@code AggregateMatchNode.hasSignal
 * (Patient, Patient)}, which requires a real patient pair (v2-roadmap.md §12; database-changes-
 * for-functional-model.md §7's Batch Signature Analysis, and proposed-functional-model-and-
 * navigation.md §9.2/§9.3's "show the resulting classification"). {@code SignatureServlet}'s
 * existing decode path ({@code patientCompare.setSignature(...)}) only populates each leaf node's
 * {@code scoreFromSignature} for display -- it never runs the classification.
 *
 * <p>This does not reimplement any field-comparison matching logic (that rule set is opaque, lives
 * entirely inside {@code mismo-match}'s leaf {@code MatchNode.score(Patient, Patient)}
 * implementations, and is fully captured by the already-decoded {@code scoreFromSignature} values
 * from {@link PatientCompare#setSignature}). It mechanically replays the same two-step aggregation
 * {@code AggregateMatchNode.score(Patient, Patient)}/{@code MatchNode.hasSignal} and
 * {@code PatientCompare.getResult()} already perform -- sum each enabled child's weighted score,
 * clamp to at most 1, zero out anything under 0.5, scale by the node's own min/max, threshold at
 * 0.5 -- using {@link MatchNode#weightScoreFromSignaturer()} in place of {@code weightScore(a, b)}
 * at the leaves. Validated against real {@code evaluation_result} rows (decoded classification
 * matches the stored, live-computed {@code calculated_classification} for the same signature).
 */
final class SignatureClassifier {

  private SignatureClassifier() {
  }

  /**
   * Classifies {@code patientCompare}'s currently-decoded signature (i.e. after
   * {@link PatientCompare#setSignature}) as {@link MatchItem#MATCH}, {@link MatchItem#POSSIBLE_MATCH},
   * or {@link MatchItem#NOT_A_MATCH} -- the same three values {@code PatientCompare.getResult()}
   * would produce from a live patient pair with an equivalent signature.
   */
  static String classify(PatientCompare patientCompare) {
    boolean matchSignal = hasSignalFromSignature(patientCompare.getMatch());
    boolean notMatchSignal = hasSignalFromSignature(patientCompare.getNotMatch());
    boolean twinSignal = hasSignalFromSignature(patientCompare.getTwin());
    boolean missingSignal = hasSignalFromSignature(patientCompare.getMissing());
    if (!matchSignal) {
      return MatchItem.NOT_A_MATCH;
    }
    if (notMatchSignal || twinSignal || missingSignal) {
      return MatchItem.POSSIBLE_MATCH;
    }
    return MatchItem.MATCH;
  }

  /** {@code AggregateMatchNode.hasSignal(Patient, Patient)}'s signature-decoded equivalent. */
  private static boolean hasSignalFromSignature(AggregateMatchNode node) {
    double weighted = weightedScoreFromSignature(node);
    return weighted >= 0.5;
  }

  /**
   * {@code MatchNode.weightScore(Patient, Patient)}'s signature-decoded equivalent, for either a
   * leaf ({@link MatchNode#weightScoreFromSignaturer()} directly) or an aggregate (its own
   * min/max-scaled {@link #aggregateScoreFromSignature}).
   */
  private static double weightedScoreFromSignature(MatchNode node) {
    if (node instanceof AggregateMatchNode) {
      double raw = aggregateScoreFromSignature((AggregateMatchNode) node);
      return raw * (node.getMaxScore() - node.getMinScore()) + node.getMinScore();
    }
    return node.weightScoreFromSignaturer();
  }

  /** {@code AggregateMatchNode.score(Patient, Patient)}'s signature-decoded equivalent. */
  private static double aggregateScoreFromSignature(AggregateMatchNode node) {
    double sum = 0;
    for (MatchNode child : node.getMatchNodeList()) {
      if (!child.isEnabled()) {
        continue;
      }
      sum += weightedScoreFromSignature(child);
    }
    if (sum > 1) {
      sum = 1;
    }
    if (sum < 0.5) {
      sum = 0;
    }
    return sum;
  }
}
