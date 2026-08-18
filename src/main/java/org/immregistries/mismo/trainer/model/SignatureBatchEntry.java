package org.immregistries.mismo.trainer.model;

/**
 * One raw {@code (signature, count)} pair within a {@link SignatureBatch}
 * (database-changes-for-functional-model.md §7). Decoding -- classification, detector scoring --
 * is computed on demand against whichever {@link Configuration} the analyst currently has
 * selected, never persisted here.
 */
public class SignatureBatchEntry {

  private int signatureBatchEntryId;
  private SignatureBatch signatureBatch;
  private String signature;
  private int count;

  public int getSignatureBatchEntryId() {
    return signatureBatchEntryId;
  }

  public void setSignatureBatchEntryId(int signatureBatchEntryId) {
    this.signatureBatchEntryId = signatureBatchEntryId;
  }

  public SignatureBatch getSignatureBatch() {
    return signatureBatch;
  }

  public void setSignatureBatch(SignatureBatch signatureBatch) {
    this.signatureBatch = signatureBatch;
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
