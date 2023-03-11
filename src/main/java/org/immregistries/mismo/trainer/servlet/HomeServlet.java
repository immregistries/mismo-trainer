package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.SoftwareVersion;

/**
 * This servlet tests a set of match test cases against a given script to give a summary of how well
 * the weights work.
 *
 * @author Nathan Bunker
 */
public class HomeServlet extends HttpServlet {
  public static final String ACTION_LOGIN = "Login";
  public static final String ACTION_LOGOUT = "Logout";

  public static final String PARAM_NAME = "name";
  public static final String PARAM_PASSWORD = "password";
  public static final String PARAM_MESSAGE = "message";

  public static final String PARAM_ACTION = "action";
  public static final String ATTRIBUTE_USER = "user";
  public static final String ATTRIUBTE_DATA_SESSION = "dataSession";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      Session dataSession = (Session) session.getAttribute(ATTRIUBTE_DATA_SESSION);
      String message = req.getParameter(PARAM_MESSAGE);
      if (dataSession == null) {
        getSessionFactory();
        dataSession = factory.openSession();
        session.setAttribute(ATTRIUBTE_DATA_SESSION, dataSession);
      }

      String action = req.getParameter(PARAM_ACTION);
      if (action != null) {
        if (action.equals(ACTION_LOGIN)) {
          String name = req.getParameter(PARAM_NAME);
          String password = req.getParameter(PARAM_PASSWORD);
          Query query = dataSession.createQuery("from User where name = ? and password = ?");
          query.setParameter(0, name);
          query.setParameter(1, password);
          List<User> userList = query.list();
          if (userList.size() > 0) {
            user = userList.get(0);
            session.setAttribute(ATTRIBUTE_USER, user);
            message = "Welcome " + user.getName() + "!";
          } else {
            message = "Unrecognized name or password, unable to login.";
          }
        } else if (action.equals(ACTION_LOGOUT)) {
          dataSession.close();
          session.invalidate();
          user = null;
          return;
        }
      }

      doHeader(out, user, message);
      out.println("  <h1>Mismo Match Toolset</h1>");
      if (user == null) {
        out.println("    <div class=\"w3-container w3-half w3-margin-top\">");
        out.println("    <div class=\"w3-container w3-card-4\">");
        out.println("    <form action=\"HomeServlet\" method=\"POST\"> ");
        out.println("    <table>");
        out.println("      <tr>");
        out.println("        <td>Name</td>");
        out.println(
            "        <td><input type=\"text\" size=\"20\" name=\""
                + PARAM_NAME
                + "\" value=\"\"/></td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <td>Password</td>");
        out.println(
            "        <td><input type=\"password\" size=\"20\" name=\""
                + PARAM_PASSWORD
                + "\" value=\"\"/></td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println(
            "        <td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\""
                + PARAM_ACTION
                + "\" value=\""
                + ACTION_LOGIN
                + "\"/></td>");
        out.println("      </tr>");
        out.println("    </table>");
        out.println("    </form>");
        out.println("    </div>");
        out.println("    </div>");
      } else {
        out.println("    <div class=\"w3-container w3-half w3-margin-top\">");
        out.println("  <h3>Primary Tools</h3>");
        out.println("  <ul>");
        out.println(
            "    <li><a href=\"CentralServlet\">Central</a>: Shows the status of the central server"
                + " that is responsible for listening to remote Island processes and reporting on"
                + " the progress of these optimizations.</li>");
        out.println(
            "    <li><a href=\"WeightSetServlet\">Test Set</a>: Allows for viewing and updating the"
                + " currently selected weight set.</li>");
        out.println(
            "    <li><a href=\"TestSetServlet\">Test Set</a>: Allows for entry and management of"
                + " test sets.</li>");
        out.println(
            "    <li><a href=\"TestSetServlet\">Review</a>: Review tests that fail in context of"
                + " similar tests. </li>");
        out.println(
            "    <li><a href=\"HomeServlet?"
                + PARAM_ACTION
                + "="
                + ACTION_LOGOUT
                + "\">Logout</a></li>");
        out.println("  </ul>");
        out.println("  <h3>Other Tools</h3>");
        out.println("  <ul>");
        out.println(
            "    <li><a href=\"TestMatchingServlet\">Test Matching</a>: Shows the results of how"
                + " well a particular matching set works.</li>");
        out.println(
            "    <li><a href=\"MatchPatientServlet\">Match Patient</a>: Shows how a single patient"
                + " is matched using the weighting system.</li>");
        out.println("    <li><a href=\"ConvertDataServlet\">Convert Data to Match Format</a></li>");
        out.println(
            "    <li><a href=\"AddressTestServlet\">Address Test</a>: Allows for looking at how"
                + " addresses are read.</li>");
        out.println("    <li><a href=\"DownloadHl7Servlet\">Download HL7</a></li>");
        out.println(
            "    <li><a href=\"GenerateWeightsServlet\">Generate Weights</a>: Starts evolutionary"
                + " algorithm that hunts for best weights. Do not click unless you are ready for"
                + " generator start.</li>");
        out.println(
            "    <li><a href=\"RandomServlet\">Random</a>: Supports creating a set of three random"
                + " patients, the second matching with the first and the third having similar"
                + " characteristics but not being a match.</li>");
        out.println(
            "    <li><a href=\"RandomScriptServlet\">Random Script</a>: Creates script with lots of"
                + " example data.</li>");
        out.println(
            "    <li><a href=\"RandomForCDCServlet\">Random for CDC Servlet</a>:  Creates data in a"
                + " spreadsheet that was requested by the CDC deduplication project.</li>");
        out.println("  </ul>");
        out.println("    </div>");

        out.println(
            "  <img src=\"images/erol-ahmed-FTy5VSGIfiQ-unsplash.jpg\" class=\"w3-round\""
                + " alt=\"Sandbox\" width=\"400\">");
        out.println(
            "<a style=\"background-color:black;color:white;text-decoration:none;padding:4px"
                + " 6px;font-family:-apple-system, BlinkMacSystemFont, &quot;San Francisco&quot;,"
                + " &quot;Helvetica Neue&quot;, Helvetica, Ubuntu, Roboto, Noto, &quot;Segoe"
                + " UI&quot;, Arial,"
                + " sans-serif;font-size:12px;font-weight:bold;line-height:1.2;display:inline-block;border-radius:3px\""
                + " href=\"https://unsplash.com/@erol?utm_medium=referral&amp;utm_campaign=photographer-credit&amp;utm_content=creditBadge\""
                + " target=\"_blank\" rel=\"noopener noreferrer\" title=\"Download free do whatever"
                + " you want high-resolution photos from Erol Ahmed\"><span"
                + " style=\"display:inline-block;padding:2px 3px\"><svg"
                + " xmlns=\"http://www.w3.org/2000/svg\""
                + " style=\"height:12px;width:auto;position:relative;vertical-align:middle;top:-2px;fill:white\""
                + " viewBox=\"0 0 32 32\"><title>unsplash-logo</title><path d=\"M10 9V0h12v9H10zm12"
                + " 5h10v18H0V14h10v9h12v-9z\"></path></svg></span><span"
                + " style=\"display:inline-block;padding:2px 3px\">Erol Ahmed</span></a>");
      }
      doFooter(out, user);
    } catch (Exception e) {
      e.printStackTrace(out);
    }
    out.close();
  }

  /**
   * Implements header html.
   */
  public static void doHeader(PrintWriter out, User user, String message) {
    out.println(
        "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\""
            + " \"http://www.w3.org/TR/html4/loose.dtd\"> ");
    out.println("<html>");
    out.println("  <head>");
    out.println("    <title>Mismo Match</title>");
    out.println("    <link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\"/>");
    out.println("  </head>");
    out.println("  <body>");
    makeMenu(out, user);
    out.println("    <div class=\"w3-container\">");
    if (message != null) {
      out.println(
          "    <div class=\"w3-panel w3-yellow\"><p class=\"w3-left-align\">"
              + message
              + "</p></div>");
    }
  }

  /**
   * Implements footer html.
   */
  public static void doFooter(PrintWriter out, User user) {
    out.println("    </div>");
    out.println("    <p></p>");
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    out.println("  <div class=\"w3-container w3-green\">");
    out.println(
        "    <p>Mismo Match v"
            + SoftwareVersion.VERSION
            + " - Current Time "
            + sdf.format(System.currentTimeMillis())
            + "</p>");
    out.println("  </div>");
    out.println("  </body>");
    out.println("</html>");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }

  private static SessionFactory factory;

  /**
   * Session factory singleton.
   */
  public static SessionFactory getSessionFactory() {
    if (factory == null) {
      factory = new AnnotationConfiguration().configure().buildSessionFactory();
    }
    return factory;
  }

  private static void makeMenu(PrintWriter out, User user) {
    out.println("    <header class=\"w3-container w3-light-grey\">");
    out.println("      <div class=\"w3-bar w3-light-grey\">");
    out.println(
        "        <a href=\"HomeServlet\" class=\"w3-bar-item w3-button w3-green\">Mismo"
            + " Match</a>");
    if (user == null) {
      out.println("        <a href=\"\" class=\"w3-bar-item w3-button\">Login</a>");
    } else {
      out.println("        <a href=\"CentralServlet\" class=\"w3-bar-item w3-button\">Central</a>");
      out.println(
          "        <a href=\"WeightSetServlet\" class=\"w3-bar-item w3-button\">Weight Script</a>");
      out.println(
          "        <a href=\"TestSetServlet\" class=\"w3-bar-item w3-button\">Test Set</a>");
      out.println("        <a href=\"ReviewServlet\" class=\"w3-bar-item w3-button\">Review</a>");
      out.println(
          "        <a href=\"HomeServlet?"
              + PARAM_ACTION
              + "="
              + ACTION_LOGOUT
              + "\" class=\"w3-bar-item w3-button\">Logout</a>");
    }
    out.println("      </div>");
    out.println("    </header>");
  }
}
