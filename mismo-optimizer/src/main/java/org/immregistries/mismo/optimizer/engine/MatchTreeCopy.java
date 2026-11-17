package org.immregistries.mismo.optimizer.engine;

import java.util.Objects;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;

/** Copies live genetic values without using the dependency's aggregate clone implementation. */
final class MatchTreeCopy {
  private MatchTreeCopy() {
  }

  static void copy(PatientCompare source, PatientCompare target) {
    MatchNode[] sources = { source.getMatch(), source.getNotMatch(), source.getTwin(), source.getMissing() };
    MatchNode[] targets = { target.getMatch(), target.getNotMatch(), target.getTwin(), target.getMissing() };
    // Validate all four trees before changing any node in the target.
    for (int i = 0; i < sources.length; i++) validate(sources[i], targets[i]);
    for (int i = 0; i < sources.length; i++) copyValues(sources[i], targets[i]);
  }

  private static void validate(MatchNode source, MatchNode target) {
    if (source.getClass() != target.getClass()
        || !Objects.equals(source.getMatchName(), target.getMatchName())
        || !Objects.equals(source.getMatchNameFull(), target.getMatchNameFull())
        || !Objects.equals(source.getFieldName(), target.getFieldName())
        || !Objects.equals(source.getFieldName2(), target.getFieldName2())
        || !Objects.equals(source.getFieldName3(), target.getFieldName3())
        || !Objects.equals(source.getFieldNameOther(), target.getFieldNameOther())
        || !Objects.equals(source.getSplitParameter(), target.getSplitParameter())
        || source.isNot() != target.isNot()) {
      throw incompatible(source);
    }
    if (source instanceof AggregateMatchNode a && target instanceof AggregateMatchNode b) {
      if (a.getMatchNodeList().size() != b.getMatchNodeList().size()) throw incompatible(source);
      for (int i = 0; i < a.getMatchNodeList().size(); i++) {
        validate(a.getMatchNodeList().get(i), b.getMatchNodeList().get(i));
      }
    }
  }

  private static IllegalArgumentException incompatible(MatchNode source) {
    return new IllegalArgumentException("Cannot copy incompatible matcher topology or detector: "
        + source.getMatchNameFull());
  }

  private static void copyValues(MatchNode source, MatchNode target) {
    // Disabling resets bounds in the engine, so restore the exact source bounds afterwards.
    target.setEnabled(source.isEnabled());
    target.setMinScore(source.getMinScore());
    target.setMaxScore(source.getMaxScore());
    if (source instanceof AggregateMatchNode a && target instanceof AggregateMatchNode b) {
      for (int i = 0; i < a.getMatchNodeList().size(); i++) {
        copyValues(a.getMatchNodeList().get(i), b.getMatchNodeList().get(i));
      }
    }
  }
}
