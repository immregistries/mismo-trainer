package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.optimizer.engine.Scorer;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.model.ScoringProfile;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.trainer.runner.RunnerScope;

/**
 * Scoring Profiles: the named, versioned fitness functions runs are steered by
 * (docs/optimization-objectives-and-scoring-profiles.md; docs/island-runner-implementation-plan.md
 * D6). Lists profiles, shows one version with its history, creates profiles, and edits by adding a
 * new version, so runs and assignments keep the version they used. A description of intent is
 * required. Only the owning organization can add versions; template profiles are readable by all.
 */
public class ScoringProfileServlet extends HomeServlet {

  public static final String PARAM_SCORING_PROFILE_ID = "scoringProfileId";
  public static final String PARAM_NAME = "name";
  public static final String PARAM_PURPOSE = "purpose";
  public static final String PARAM_DESCRIPTION = "description";

  public static final String ACTION_CREATE = "Create Profile";
  public static final String ACTION_SAVE_VERSION = "Save as New Version";

  /** Weights are kept in a sane range; the diagonal must be positive or the score is meaningless. */
  private static final int MAX_WEIGHT = 1000;

  private static final String[] EXPECTED = { "Should Match", "Should be Possible", "Should Not Match" };
  private static final String[] ACTUAL = { "Matched", "Possible", "Not Matched" };

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }
      Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
      String message = null;
      ScoringProfile selected = null;
      if (req.getParameter(PARAM_SCORING_PROFILE_ID) != null) {
        try {
          selected = RunnerScope.loadScoringProfile(dataSession,
              Integer.parseInt(req.getParameter(PARAM_SCORING_PROFILE_ID)), user.getOrganization());
        } catch (NumberFormatException e) {
          selected = null;
        }
        if (selected == null) {
          message = "Scoring profile not found.";
        }
      }

      String action = req.getParameter(PARAM_ACTION);
      if (ACTION_CREATE.equals(action) || (ACTION_SAVE_VERSION.equals(action) && selected != null)) {
        ScoringProfile previous = ACTION_CREATE.equals(action) ? null : selected;
        if (previous != null && !RunnerScope.isEditable(previous, user)) {
          message = "Only the organization that owns this profile can change it. Create your own profile instead.";
        } else {
          String name = trim(req.getParameter(PARAM_NAME));
          String purpose = trim(req.getParameter(PARAM_PURPOSE));
          String description = trim(req.getParameter(PARAM_DESCRIPTION));
          int[][] weights = readWeights(req);
          String problem = validate(name, purpose, description, weights);
          if (problem != null) {
            message = problem;
          } else {
            Transaction transaction = dataSession.beginTransaction();
            selected = RunnerScope.saveProfile(dataSession, previous, name, purpose, description, weights, user);
            transaction.commit();
            message = previous == null ? "Created \"" + name + "\"."
                : "Saved version " + selected.getVersion() + ". Runs and assignments that used an earlier version"
                    + " keep it; choose this version on a runner's assignment to use it.";
          }
        }
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      if (selected == null) {
        printList(out, dataSession, user);
      } else {
        printProfile(out, dataSession, user, selected);
      }
      out.println("    </div>");
      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    } finally {
      out.close();
      teardown(req, resp);
    }
  }

  private void printList(PrintWriter out, Session dataSession, User user) {
    out.println("    <h1 class=\"aira-page-title\">Scoring Profiles</h1>");
    out.println("    <p>A scoring profile is the fitness function an optimization run is steered by: how much each"
        + " expected/actual combination is rewarded or penalized. Every run records the profile version it used, and"
        + " scores are only comparable between runs with the same version. Read"
        + " <code>docs/optimization-objectives-and-scoring-profiles.md</code> before creating or changing one: the"
        + " national measurement matrix is deliberate, not a mistake.</p>");
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Profile</th><th>Purpose</th><th>Version</th><th>Owner</th><th>Weights</th></tr>");
    for (ScoringProfile profile : RunnerScope.listScoringProfiles(dataSession, user.getOrganization())) {
      out.println("      <tr>");
      out.println("        <td><a href=\"ScoringProfileServlet?" + PARAM_SCORING_PROFILE_ID + "="
          + profile.getScoringProfileId() + "\">" + escapeHtml(profile.getName()) + "</a></td>");
      out.println("        <td>" + escapeHtml(profile.getPurpose()) + "</td>");
      out.println("        <td>" + profile.getVersion() + "</td>");
      out.println("        <td>" + owner(profile, user) + "</td>");
      out.println("        <td><pre>" + escapeHtml(ScoringWeights.format(ScoringWeights.fromJson(profile.getScoringWeightsJson())))
          + "</pre></td>");
      out.println("      </tr>");
    }
    out.println("    </table>");

    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Create a profile</h2>");
    out.println("      <p>Starts from the national measurement weights. Steering toward a different objective"
        + " (for example production calibration) belongs in its own, clearly named profile.</p>");
    printForm(out, null, "", RunnerScope.PURPOSE_EXPERIMENTAL, "", Scorer.defaultWeights(), ACTION_CREATE);
    out.println("    </section>");
  }

  private void printProfile(PrintWriter out, Session dataSession, User user, ScoringProfile profile) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    int[][] weights = ScoringWeights.fromJson(profile.getScoringWeightsJson());
    out.println("    <p><a href=\"ScoringProfileServlet\">All scoring profiles</a></p>");
    out.println("    <h1 class=\"aira-page-title\">" + escapeHtml(profile.getName()) + " &middot; version "
        + profile.getVersion() + "</h1>");
    out.println("    <table>");
    out.println("      <tr><th align=\"left\">Purpose</th><td>" + escapeHtml(profile.getPurpose()) + "</td></tr>");
    out.println("      <tr><th align=\"left\">Owner</th><td>" + owner(profile, user) + "</td></tr>");
    out.println("      <tr><th align=\"left\">Saved</th><td>" + sdf.format(profile.getCreatedAt())
        + (profile.getCreatedByUser() == null ? " (seeded)" : " by " + escapeHtml(who(profile.getCreatedByUser())))
        + "</td></tr>");
    out.println("      <tr><th align=\"left\" valign=\"top\">Intent</th><td>" + escapeHtml(profile.getDescription())
        + "</td></tr>");
    out.println("    </table>");
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>&nbsp;</th><th>" + ACTUAL[0] + "</th><th>" + ACTUAL[1] + "</th><th>" + ACTUAL[2] + "</th></tr>");
    for (int i = 0; i < 3; i++) {
      out.println("      <tr><th align=\"left\">" + EXPECTED[i] + "</th><td>" + weights[i][0] + "</td><td>"
          + weights[i][1] + "</td><td>" + weights[i][2] + "</td></tr>");
    }
    out.println("    </table>");

    List<ScoringProfile> versions = RunnerScope.listProfileVersions(dataSession, profile.getRootScoringProfileId(),
        user.getOrganization());
    if (versions.size() > 1) {
      out.println("    <h2>Versions</h2>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr><th>Version</th><th>Name</th><th>Purpose</th><th>Saved</th><th>Weights</th></tr>");
      for (ScoringProfile version : versions) {
        boolean current = version.getScoringProfileId() == profile.getScoringProfileId();
        out.println("      <tr" + (current ? " class=\"pass\"" : "") + ">");
        out.println("        <td>" + (current ? String.valueOf(version.getVersion())
            : "<a href=\"ScoringProfileServlet?" + PARAM_SCORING_PROFILE_ID + "=" + version.getScoringProfileId()
                + "\">" + version.getVersion() + "</a>") + "</td>");
        out.println("        <td>" + escapeHtml(version.getName()) + "</td>");
        out.println("        <td>" + escapeHtml(version.getPurpose()) + "</td>");
        out.println("        <td>" + sdf.format(version.getCreatedAt()) + "</td>");
        out.println("        <td><code>" + escapeHtml(version.getScoringWeightsJson()) + "</code></td>");
        out.println("      </tr>");
      }
      out.println("    </table>");
    }

    if (RunnerScope.isEditable(profile, user)) {
      out.println("    <section class=\"aira-panel\">");
      out.println("      <h2 class=\"aira-panel__title\">Change this profile</h2>");
      out.println("      <p>Saving adds version " + (versions.get(0).getVersion() + 1) + ". Nothing that used an"
          + " earlier version changes" + (profile.isTemplate() ? "; this is a template, so every organization sees"
              + " the new version" : "") + ".</p>");
      printForm(out, profile, profile.getName(), profile.getPurpose(), profile.getDescription(), weights,
          ACTION_SAVE_VERSION);
      out.println("    </section>");
    }
  }

  private void printForm(PrintWriter out, ScoringProfile profile, String name, String purpose, String description,
      int[][] weights, String action) {
    out.println("      <form action=\"ScoringProfileServlet\" method=\"POST\">");
    if (profile != null) {
      out.println("        <input type=\"hidden\" name=\"" + PARAM_SCORING_PROFILE_ID + "\" value=\""
          + profile.getScoringProfileId() + "\"/>");
    }
    out.println("        <table>");
    out.println("          <tr><td>Name</td><td><input type=\"text\" size=\"60\" maxlength=\"150\" name=\"" + PARAM_NAME
        + "\" value=\"" + escapeHtml(name) + "\"/></td></tr>");
    out.println("          <tr><td>Purpose</td><td><select name=\"" + PARAM_PURPOSE + "\">");
    for (String option : RunnerScope.PURPOSES) {
      out.println("            <option value=\"" + option + "\"" + (option.equals(purpose) ? " selected=\"true\"" : "")
          + ">" + option + "</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td valign=\"top\">Intent (required)</td><td><textarea name=\"" + PARAM_DESCRIPTION
        + "\" rows=\"4\" cols=\"70\" placeholder=\"What this profile steers toward, and why\">"
        + escapeHtml(description) + "</textarea></td></tr>");
    out.println("          <tr><td valign=\"top\">Weights</td><td><table>");
    out.println("            <tr><th>&nbsp;</th><th>" + ACTUAL[0] + "</th><th>" + ACTUAL[1] + "</th><th>" + ACTUAL[2]
        + "</th></tr>");
    for (int i = 0; i < 3; i++) {
      out.print("            <tr><th align=\"left\">" + EXPECTED[i] + "</th>");
      for (int j = 0; j < 3; j++) {
        out.print("<td><input type=\"number\" min=\"" + -MAX_WEIGHT + "\" max=\"" + MAX_WEIGHT + "\" size=\"5\" name=\""
            + weightParam(i, j) + "\" value=\"" + weights[i][j] + "\"/></td>");
      }
      out.println("</tr>");
    }
    out.println("          </table></td></tr>");
    out.println("          <tr><td></td><td><input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + action
        + "\"/></td></tr>");
    out.println("        </table>");
    out.println("      </form>");
  }

  private static String weightParam(int i, int j) {
    return "w" + i + j;
  }

  /** @return the posted weights, or {@code null} if any is missing or not a whole number */
  private static int[][] readWeights(HttpServletRequest req) {
    int[][] weights = new int[3][3];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        try {
          weights[i][j] = Integer.parseInt(trim(req.getParameter(weightParam(i, j))));
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return weights;
  }

  private static String validate(String name, String purpose, String description, int[][] weights) {
    if (name.isEmpty() || name.length() > 150) {
      return "Give the profile a name of up to 150 characters.";
    }
    if (!RunnerScope.PURPOSES.contains(purpose)) {
      return "Choose a purpose.";
    }
    if (description.isEmpty()) {
      return "Describe what this profile steers toward and why. The description is required so every score it"
          + " produces can be read in context.";
    }
    if (weights == null) {
      return "Every weight must be a whole number.";
    }
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        if (Math.abs(weights[i][j]) > MAX_WEIGHT) {
          return "Weights must be between " + -MAX_WEIGHT + " and " + MAX_WEIGHT + ".";
        }
      }
      if (weights[i][i] <= 0) {
        return "The weights for a correct answer (" + EXPECTED[i] + " / " + ACTUAL[i] + ") must be positive;"
            + " the score is measured against them.";
      }
    }
    return null;
  }

  private static String owner(ScoringProfile profile, User user) {
    String name = escapeHtml(profile.getOrganization().getName());
    if (profile.isTemplate()) {
      return name + " (template)";
    }
    return RunnerScope.isEditable(profile, user) ? "Your organization" : name;
  }

  private static String who(User user) {
    return user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }
}
