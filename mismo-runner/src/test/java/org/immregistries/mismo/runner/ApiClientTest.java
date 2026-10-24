package org.immregistries.mismo.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ApiClientTest {

  @Test
  public void httpsIsAcceptedAnywhere() {
    assertNull(ApiClient.checkServerUrl("https://informatics.immregistries.org/mismo"));
    assertNull(ApiClient.checkServerUrl("https://informatics.immregistries.org/mismo/"));
  }

  @Test
  public void plainHttpOnlyForThisComputer() {
    assertNull(ApiClient.checkServerUrl("http://localhost:8080/mismo"));
    assertNull(ApiClient.checkServerUrl("http://127.0.0.1:8080/mismo"));
    assertNotNull(ApiClient.checkServerUrl("http://informatics.immregistries.org/mismo"));
    assertNotNull(ApiClient.checkServerUrl("informatics.immregistries.org/mismo"));
    assertNotNull(ApiClient.checkServerUrl("ftp://localhost/mismo"));
  }

  @Test
  public void trailingSlashesAreDropped() {
    assertEquals("https://x.org/mismo", ApiClient.stripTrailingSlash(" https://x.org/mismo// "));
  }
}
