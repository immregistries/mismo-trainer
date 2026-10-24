package org.immregistries.mismo.trainer.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RunnerCredentialSupportTest {

  @Test
  public void pairingCodesUseTheUnambiguousAlphabet() {
    for (int i = 0; i < 200; i++) {
      String code = RunnerCredentialSupport.generatePairingCode();
      assertTrue(code, code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}"));
      assertEquals(code, RunnerCredentialSupport.normalizePairingCode(code));
    }
  }

  @Test
  public void typedCodesAreNormalized() {
    assertEquals("ABCD-EFGH", RunnerCredentialSupport.normalizePairingCode(" abcd efgh "));
    assertEquals("ABCD-EFGH", RunnerCredentialSupport.normalizePairingCode("ABCDEFGH"));
    assertNull(RunnerCredentialSupport.normalizePairingCode("ABCD-EFG"));
    assertNull("O and 0 are not in the alphabet", RunnerCredentialSupport.normalizePairingCode("ABCD-EFG0"));
    assertNull(RunnerCredentialSupport.normalizePairingCode(null));
  }

  @Test
  public void credentialsAreRandomAndOnlyTheirHashIsStoredForm() {
    String a = RunnerCredentialSupport.generateCredential();
    String b = RunnerCredentialSupport.generateCredential();
    assertTrue(a.startsWith("mrn_"));
    assertEquals(4 + 43, a.length());
    assertNotEquals(a, b);
    assertEquals(64, RunnerCredentialSupport.hash(a).length());
    assertEquals(RunnerCredentialSupport.hash(a), RunnerCredentialSupport.hash(a));
  }
}
