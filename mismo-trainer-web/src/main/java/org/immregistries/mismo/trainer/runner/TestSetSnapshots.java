package org.immregistries.mismo.trainer.runner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.protocol.Hashes;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.TestSetSnapshot;

/**
 * Makes the immutable copy of a Test Set that a run uses (docs/island-runner-implementation-plan.md
 * D3). Only cases with a scoreable expected status are included, in case-id order, as their stored
 * strings. The JSON is stored as the exact bytes served, so its hash never drifts.
 */
public final class TestSetSnapshots {

  private TestSetSnapshots() {
  }

  /** Serializes {@code matchSet}'s current scoreable cases the way a snapshot stores them. */
  @SuppressWarnings("unchecked")
  public static Messages.TestSetContent buildContent(Session dataSession, MatchSet matchSet) {
    Query query = dataSession.createQuery(
        "from MatchItem where matchSet = ? and expectStatus in (?, ?, ?) order by matchItemId");
    query.setParameter(0, matchSet);
    query.setParameter(1, MatchItem.MATCH);
    query.setParameter(2, MatchItem.POSSIBLE_MATCH);
    query.setParameter(3, MatchItem.NOT_A_MATCH);
    List<Messages.TestCase> cases = new ArrayList<Messages.TestCase>();
    for (org.immregistries.mismo.trainer.model.MatchItem row
        : (List<org.immregistries.mismo.trainer.model.MatchItem>) query.list()) {
      cases.add(new Messages.TestCase(row.getLabel(), row.getDescription(), row.getExpectStatus(),
          row.getPatientDataA(), row.getPatientDataB()));
    }
    return new Messages.TestSetContent(Protocol.TEST_SET_FORMAT_VERSION, matchSet.getMatchSetId(),
        matchSet.getLabel(), cases);
  }

  /**
   * Returns the snapshot of {@code matchSet} as it is now: an existing one if a snapshot with the
   * same content already exists, otherwise a new row. Caller owns the transaction.
   */
  public static TestSetSnapshot snapshot(Session dataSession, MatchSet matchSet) {
    Messages.TestSetContent content = buildContent(dataSession, matchSet);
    String json = Protocol.gson().toJson(content);
    String sha256 = Hashes.sha256Hex(json);

    Query query = dataSession.createQuery("from TestSetSnapshot where matchSet = ? and contentSha256 = ?");
    query.setParameter(0, matchSet);
    query.setParameter(1, sha256);
    TestSetSnapshot existing = (TestSetSnapshot) query.uniqueResult();
    if (existing != null) {
      return existing;
    }
    TestSetSnapshot snapshot = new TestSetSnapshot();
    snapshot.setMatchSet(matchSet);
    snapshot.setContentSha256(sha256);
    snapshot.setCaseCount(content.cases().size());
    snapshot.setLifecycleStatus(matchSet.getLifecycleStatus());
    snapshot.setContentJson(json);
    snapshot.setCreatedAt(new Date());
    dataSession.save(snapshot);
    return snapshot;
  }
}
