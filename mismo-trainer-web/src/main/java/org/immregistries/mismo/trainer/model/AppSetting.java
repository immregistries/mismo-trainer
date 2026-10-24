package org.immregistries.mismo.trainer.model;

/**
 * A minimal key/value runtime setting, currently used to hold the InteropHub
 * base URL so it can be changed without a redeploy -- following the "Clear"
 * integration precedent in InteropHub-Client's docs (DB-driven Hub URL
 * rather than a build-time property).
 */
public class AppSetting {

  private String settingKey;
  private String settingValue;

  public String getSettingKey() {
    return settingKey;
  }

  public void setSettingKey(String settingKey) {
    this.settingKey = settingKey;
  }

  public String getSettingValue() {
    return settingValue;
  }

  public void setSettingValue(String settingValue) {
    this.settingValue = settingValue;
  }
}
