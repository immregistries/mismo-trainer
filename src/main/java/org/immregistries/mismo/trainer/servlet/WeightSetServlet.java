package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.MatchSet;
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

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
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
      Session dataSession = (Session) session.getAttribute(TestSetServlet.ATTRIUBTE_DATA_SESSION);
      String testScript = req.getParameter("testScript");
      if (testScript == null) {
        testScript = (String) session.getAttribute("testScript");
        if (testScript == null) {
          testScript = "";
        }
      }
      String creatureScript = req.getParameter(TestMatchingServlet.PARAM_CREATURE_SCRIPT);
      if (creatureScript == null) {
        creatureScript =
            (String) session.getAttribute(TestMatchingServlet.ATTRIBUTE_CREATURE_SCRIPT);
      } else {
        session.setAttribute(TestMatchingServlet.ATTRIBUTE_CREATURE_SCRIPT, creatureScript);
        session.removeAttribute(TestSetServlet.ATTRIBUTE_MATCH_ITEM_LIST);
      }

      MatchSet matchSetSelected = null;
      if (req.getParameter(PARAM_MATCH_SET_ID) != null) {
        matchSetSelected =
            (MatchSet)
                dataSession.get(
                    MatchSet.class, Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID)));
      }

      String action = req.getParameter(PARAM_ACTION);

      HomeServlet.doHeader(out, user, null);

      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
      if (matchSetSelected != null) {
        out.println("<h2>" + matchSetSelected.getLabel() + "</h2>");

        Query query =
            dataSession.createQuery(
                "from MatchItem where matchSet = ? order by dataSource, updateDate");
        query.setParameter(0, matchSetSelected);
        List<MatchItem> matchItemList = query.list();
        if (matchItemList.size() > 0) {
          PatientCompare patientCompare = new PatientCompare();

          if (creatureScript != null && creatureScript.length() > 0) {
            patientCompare.readScript(creatureScript);
          }
        }
      }

      out.println("<h1>Weight Script</h1>");
      if (creatureScript != null) {
        out.println("<div class=\"scrollbox\">");
        out.println(creatureScript);
        out.println("</div>");
      }
      out.println("<h4>Load</h4>");
      out.println("    <form action=\"WeightSetServlet\" method=\"POST\"> ");
      out.println("    <table>");
      out.println(
          "      <tr><td valign=\"top\">Weight Script</td><td><textarea name=\""
              + TestMatchingServlet.PARAM_CREATURE_SCRIPT
              + "\" cols=\"70\" rows=\"1\" wrap=\"off\">"
              + (creatureScript == null ? "" : creatureScript)
              + "</textarea></td></tr>");
      out.println(
          "      <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"submit\""
              + " value=\"Submit\"></td></tr>");
      out.println("    </table>");

      HomeServlet.doFooter(out, user);

      out.println("  </body>");
      out.println("</html>");
    } catch (Exception e) {
      e.printStackTrace(out);
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
