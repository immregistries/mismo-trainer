package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.Island;
import org.immregistries.mismo.trainer.model.Creature;
import org.immregistries.mismo.trainer.model.Scorer;
import org.immregistries.mismo.trainer.model.World;

/**
 * This is the original optimization servlet. The recommendation now is to run
 * this using the command line. This gives more control and feedback during the
 * optimization process.
 * 
 * @author Nathan Bunker
 * 
 */
public class GenerateWeightsServlet extends HomeServlet {
  public static final String POSSIBLE_MATCH = "Possible Match";
  public static final String NOT_A_MATCH = "Not a Match";
  public static final String MATCH = "Match";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      String script = req.getParameter("script");
      if (script == null) {
        script = (String) session.getAttribute("script");
        if (script == null) {
          script = "";
        }
      }

      HomeServlet.doHeader(out, user, null);
      out.println("    <script>");
      out.println("      function toggleLayer(whichLayer) ");
      out.println("      {");
      out.println("        var elem, vis;");
      out.println("        if (document.getElementById) ");
      out.println("          elem = document.getElementById(whichLayer);");
      out.println("        else if (document.all) ");
      out.println("          elem = document.all[whichLayer] ");
      out.println("        else if (document.layers) ");
      out.println("          elem = document.layers[whichLayer]");
      out.println("        vis = elem.style;");
      out.println(
          "        if (vis.display == '' && elem.offsetWidth != undefined && elem.offsetHeight != undefined) ");
      out.println(
          "          vis.display = (elem.offsetWidth != 0 && elem.offsetHeight != 0) ? 'block' : 'none';");
      out.println(
          "        vis.display = (vis.display == '' || vis.display == 'block') ? 'none' : 'block';");
      out.println("      }");
      out.println("    </script>");
      out.println("    <h1>Generate Weights</h1>");

      out.println("    <form action=\"GenerateWeightsServlet\" method=\"POST\"> ");
      out.println("    <table>");
      boolean weightsChanged = false;
      int[][] weights = Scorer.getWeights();
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          if (req.getParameter("" + i + j) != null) {
            int paramValue = Integer.parseInt(req.getParameter("" + i + j));
            if (paramValue != weights[i][j]) {
              weightsChanged = true;
            }
            weights[i][j] = paramValue;
          }
        }
      }
      out.println(
          "      <tr><th>&nbsp;</th><th>Matched</th><th>Possible</th><th>Not Matched</th></tr>");
      out.println("      <tr><th>Should Match    </th><td><input type=\"text\" name=\"00\" value=\""
          + weights[0][0] + "\"></td><td><input type=\"text\" name=\"01\" value=\"" + weights[0][1]
          + "\"><td><input type=\"text\" name=\"02\" value=\"" + weights[0][2] + "\"></td></td>");
      out.println("      <tr><th>Should Possible </th><td><input type=\"text\" name=\"10\" value=\""
          + weights[1][0] + "\"></td><td><input type=\"text\" name=\"11\" value=\"" + weights[1][1]
          + "\"><td><input type=\"text\" name=\"12\" value=\"" + weights[1][2] + "\"></td></td>");
      out.println("      <tr><th>Should Not Match</th><td><input type=\"text\" name=\"20\" value=\""
          + weights[2][0] + "\"></td><td><input type=\"text\" name=\"21\" value=\"" + weights[2][1]
          + "\"><td><input type=\"text\" name=\"22\" value=\"" + weights[2][2] + "\"></td></td>");
      out.println("      </tr>");
      out.println(
          "      <tr><td colspan=\"4\" valign=\"top\">Stop Generator <input type=\"checkbox\" name=\"stop\" value=\"stop\""
              + (req.getParameter("stop") != null ? " checkded" : "") + "></td></tr>");
      out.println(
          "      <tr><td colspan=\"4\" align=\"right\"><input type=\"submit\" name=\"submit\" value=\"Refresh\"></td></tr>");
      out.println("    </table>");
      out.println("    </form>");
      World world = (World) getServletContext().getAttribute("world");
      if (world != null && !world.isKeepRunning()) {
        getServletContext().removeAttribute("world");
        world = null;
      }
      if (world == null) {
        List<MatchItem> matchItemList;

        BufferedReader in = null;
        in = new BufferedReader(
            new InputStreamReader(this.getClass().getResourceAsStream("MIIS-E2.txt")));
        if (in != null && req.getParameter("stop") == null) {
          matchItemList = Island.readSourceFile(in);
        } else {
          matchItemList = new ArrayList<MatchItem>();
        }
        session.setAttribute(TestMatchingServlet.ATTRIBUTE_MATCH_TEST_CASE_LIST, matchItemList);

        int worldSize = 1000;
        if (req.getParameter("worldSize") != null) {
          worldSize = Integer.parseInt(req.getParameter("worldSize"));
        }
        world = new World(worldSize, "", "");
        world.setMatchItemList(matchItemList);
        world.start();
        getServletContext().setAttribute("world", world);
      }
      world.setKeepRunning(req.getParameter("stop") == null);
      Creature[] creatures = world.getCreaturesCopy();
      DecimalFormat decimalFormat = new DecimalFormat("#0.0000000");

      out.println("<p>Last Message = " + world.getLastMessage() + "</p>");
      out.println("<p>Generation = " + world.getGeneration() + "</p>");
      if (creatures != null) {
        out.println("    <br>");
        out.println("    <table border=\"1\" cellspacing=\"0\">");
        out.println(
            "      <tr><th>Position</th><th>Generation</th><th>Score</th><th>Hash</th></tr>");
        for (int i = 0; i < 100 && i < creatures.length; i++) {
          out.println("      <tr>");
          out.println(
              "        <td><a href=\"javascript:toggleLayer('T" + i + "');\">" + i + "</td>");
          out.println("        <td>" + creatures[i].getGeneration() + "</td>");
          out.println(
              "        <td>" + decimalFormat.format((creatures[i].getScore() * 100.0)) + "</td>");
          out.println("        <td>" + creatures[i].hashCode() + "</td>");
          out.println("      </tr>");
          out.println("      <tr style=\"display:none\" id=\"T" + i + "\">");
          out.println("        <td colspan=\"3\">");
          out.println("        <div class=\"scrollbox\">");
          {
            PatientCompare patientCompare = creatures[i].getPatientCompare();
            MatchNode match = creatures[i].getPatientCompare().getMatch();
            MatchNode notMatch = creatures[i].getPatientCompare().getNotMatch();
            MatchNode twin = creatures[i].getPatientCompare().getTwin();
            MatchNode missing = creatures[i].getPatientCompare().getMissing();
            {
              out.println("<table border=\"1\" cellspacing=\"0\">");
              out.println("<tr><td valign=\"top\">Match</td>");
              printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(),
                  match, "match");
              out.println("    </tr>");
              out.println("<tr><td valign=\"top\">Not a Match</td>");
              printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(),
                  notMatch, "notmatch");
              out.println("    </tr>");
              out.println("<tr><td valign=\"top\">Twin</td>");
              printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(),
                  twin, "twin");
              out.println("    </tr>");
              out.println("<tr><td valign=\"top\">Missing</td>");
              printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(),
                  missing, "missing");
              out.println("    </tr>");
              out.println("    </table>");
              out.println("    <br>");
              Scorer scorer = creatures[i].getScorer();
              out.println("    <table border=\"1\" cellspacing=\"0\">");
              out.println(
                  "      <tr><th>&nbsp;</th><th>Matched</th><th>Possible</th><th>Not Matched</th></tr>");
              int[][] c = scorer.getCountTable();
              out.println("      <tr><th>Should Match</th><td>" + c[0][0] + "</td><td>" + c[0][1]
                  + "<td>" + c[0][2] + "</td></td>");
              out.println("      <tr><th>Should Possible</th><td>" + c[1][0] + "</td><td>" + c[1][1]
                  + "<td>" + c[1][2] + "</td></td>");
              out.println("      <tr><th>Should Not Match</th><td>" + c[2][0] + "</td><td>"
                  + c[2][1] + "<td>" + c[2][2] + "</td></td>");
              out.println("      </tr>");
              out.println("    </table>");

            }
          }
          out.println("        </div>");
          out.println("        </td>");
          out.println("      </tr>");
        }
      }
      out.println("    </table>");
      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      e.printStackTrace(out);
    }
    out.close();
  }

  protected static void printAggregateNode(PrintWriter out, Patient patientA, Patient patientB,
      MatchNode node, String name) {
    DecimalFormat decimalFormat = new DecimalFormat("0.00");
    out.println("<td>");
    if (node instanceof AggregateMatchNode) {
      AggregateMatchNode amNode = (AggregateMatchNode) node;
      out.println("<table border=\"1\" cellspacing=\"0\">");
      out.println("<tr><th>" + amNode.getMatchName()
          + "</th><th>Min W</th><th>Max W</th><th>&nbsp;</th></tr>");
      for (MatchNode childNode : amNode.getMatchNodeList()) {
        String childName = name + "." + childNode.getMatchName();
        out.println("<tr>");
        out.println("<td valign=\"top\">" + childNode.getMatchName() + "</td>");
        out.println(
            "<td valign=\"top\">" + decimalFormat.format(childNode.getMinScore()) + "</td>");
        out.println(
            "<td valign=\"top\">" + decimalFormat.format(childNode.getMaxScore()) + "</td>");
        printAggregateNode(out, patientA, patientB, childNode, childName);
        out.println("</tr>");
      }
      out.println("</table>");
    } else {
      out.println("&nbsp;");
    }
    out.println("</td>");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }
}
