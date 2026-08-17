package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * Trainer-owned local user record. Attribution/session data lives here;
 * identity itself is authenticated by InteropHub via {@link #hubUserId}.
 * <p>
 * {@code name} and {@code password} are the legacy v1 login columns; they
 * remain mapped (unused by v2 logic) until the Phase 7 cleanup drops them.
 */
public class User {

  private int userId;
  private String name;
  private String email;
  private String password;
  private String hubUserId;
  private Organization organization;
  private String displayName;
  private String firstName;
  private String lastName;
  private String title;
  private Date createdAt;
  private Date updatedAt;
  private Date lastLoginAt;

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getHubUserId() {
    return hubUserId;
  }

  public void setHubUserId(String hubUserId) {
    this.hubUserId = hubUserId;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Date getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Date lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }
}
