package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A {@link Configuration} that a {@link User} has loaded, and when they last did. One row per
 * (user, configuration): reselecting moves {@code selectedAt} forward rather than adding a row.
 * The most recent row is what gets loaded again at the start of each new session.
 */
public class UserConfigurationRecent {

  private int userConfigurationRecentId;
  private User user;
  private Configuration configuration;
  private Date selectedAt;

  public int getUserConfigurationRecentId() {
    return userConfigurationRecentId;
  }

  public void setUserConfigurationRecentId(int userConfigurationRecentId) {
    this.userConfigurationRecentId = userConfigurationRecentId;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public Date getSelectedAt() {
    return selectedAt;
  }

  public void setSelectedAt(Date selectedAt) {
    this.selectedAt = selectedAt;
  }
}
