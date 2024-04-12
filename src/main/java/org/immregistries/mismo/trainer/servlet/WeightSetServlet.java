package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.User;

/**
 * This servlet tests a set of match test cases against a given script to give a summary of how well
 * the weights work.
 *
 * @author Nathan Bunker
 */
public class WeightSetServlet extends HomeServlet {

  public static final String PARAM_MATCH_ITEM_ID = "matchItemId";
  public static final String PARAM_MATCH_SET_ID = "matchSetId";

  public static final String PARAM_CONFIGURATION_ID = "configurationId";
  public static final String PARAM_CONFIGURATION_SCRIPT = "configurationScript";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
        
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }

      String message = null;
      if (req.getParameter(PARAM_CONFIGURATION_SCRIPT) != null) {
        PatientCompare patientCompare = new PatientCompare();
        session.setAttribute("patientCompare", patientCompare);
        patientCompare.getConfiguration().setConfigurationScript(req.getParameter(PARAM_CONFIGURATION_SCRIPT));
        patientCompare.getConfiguration().setup();
        message = "Manual configuration loaded";
      }
      HomeServlet.doHeader(out, user, message);

      PatientCompare patientCompare = (PatientCompare) session.getAttribute("patientCompare");

      out.println("<h1>Configuration</h1>");
      if (patientCompare != null) {
        out.println("<p>Configuration is loaded.</p>");
      }
      out.println("<h4>Manual Load</h4>");
      out.println("    <form action=\"WeightSetServlet\" method=\"POST\"> ");
      out.println("    <table>");
      out.println(
          "      <tr><td valign=\"top\">Weight Script</td><td><textarea name=\""
              + PARAM_CONFIGURATION_SCRIPT
              + "\" cols=\"45\" rows=\"25\" wrap=\"off\">"
              + (patientCompare == null ? "" : patientCompare.getConfiguration().getConfigurationScript())
              + "</textarea></td></tr>");
      out.println(
          "      <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"submit\""
              + " value=\"Submit\"></td></tr>");
      out.println("    </table>");

      HomeServlet.doFooter(out, req);

      out.println("  </body>");
      out.println("</html>");
    } catch (Exception e) {
      e.printStackTrace(out);
    } finally {
      teardown(req, resp);
    }
    out.close();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }

  private static SessionFactory factory;

  /**
   * Establises session factory singleton.
   */
  public static SessionFactory getSessionFactory() {
    if (factory == null) {

      factory = new Configuration().configure().buildSessionFactory();
    }
    return factory;
  }
}
