package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;

/**
 * This was the original servlet that demonstrated how the matching worked for
 * one example.
 * 
 * @author Nathan Bunker
 * 
 */
public class SignatureServlet extends HomeServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setup(req, resp);
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        HttpSession session = req.getSession(true);
        User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
        Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
        try {

            PatientCompare patientCompare = (PatientCompare) session.getAttribute(ATTRIBUTE_PATIENT_COMPARE);

            String signature = req.getParameter("signature");
            if (signature != null && !signature.equals("")) {
                patientCompare.setSignature(signature);
            } else {
                signature = "";
            }

            out.println("    <h1>Signature</h1>");
            out.println("    <form action=\"SignatureServlet\" method=\"POST\"> ");
            out.println("    Signature: <input type=\"text\" name=\"signature\" value=\""
                    + signature + "\" size=\"50\"/>");
            out.println("    <input type=\"submit\" name=\"action\" value=\"View\"/><br/>");

            if (!signature.equals("")) {
                out.println("  <p>Signature: <strong>" + signature + "</strong>: ");
                if (patientCompare.getScoreFromSignatureList() == null) {
                    out.println("No scores available - list is null");
                } else if (patientCompare.getScoreFromSignatureList().isEmpty()) {
                    out.println("No scores available");
                } else {
                    out.println("Scores: ");
                    for (Double d : patientCompare.getScoreFromSignatureList()) {
                        out.println(d + ", ");
                    }
                }
                out.println("</p>");
            }
            {
                out.println("<table border=\"1\" cellspacing=\"0\">");
                out.println("<tr><td valign=\"top\">Match</td>");
                printAggregateNode(out, patientCompare.getMatch(), "match");
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Not a Match</td>");
                printAggregateNode(out, patientCompare.getNotMatch(), "notmatch");
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Twin</td>");
                printAggregateNode(out, patientCompare.getTwin(), "twin");
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Missing</td>");
                printAggregateNode(out, patientCompare.getMissing(), "missing");
                out.println("    </tr>");
                out.println("    </table>");
            }
            out.println("    </form>");

            HomeServlet.doFooter(out, req);
        } catch (Exception e) {
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        } finally {
            teardown(req, resp);
            out.close();
        }
    }

    private void printAggregateNode(PrintWriter out,
            MatchNode node, String name) {
        DecimalFormat df = new DecimalFormat("0.000");
        out.println("<td>");
        if (node instanceof AggregateMatchNode) {
            AggregateMatchNode amNode = (AggregateMatchNode) node;
            out.println("<table border=\"1\" cellspacing=\"0\">");
            out.println("<tr><th>" + amNode.getMatchName()
                    + "</th><th>W Score</th><th>Min W</th><th>Max W</th><th>Score</th><th>&nbsp;</th></tr>");
            for (MatchNode childNode : amNode.getMatchNodeList()) {
                String childName = name + "." + childNode.getMatchName();
                out.println("<tr>");
                out.println("<td valign=\"top\">" + childNode.getMatchName() + "</td>");
                if (childNode.isEnabled()) {
                    out.println("<td valign=\"top\">" + df.format(childNode.weightScoreFromSignaturer()) + "</td>");
                    out.println("<td valign=\"top\"><input type=\"text\" name=\"min_" + childName
                            + "\" size=\"5\" value=\"" + childNode.getMinScore() + "\"</td>");
                    out.println("<td valign=\"top\"><input type=\"text\" name=\"max_" + childName
                            + "\" size=\"5\" value=\"" + childNode.getMaxScore() + "\"</td>");
                    out.println("" + printScore(childNode.getScoreFromSignature()) + "</td>");
                    printAggregateNode(out, childNode, childName);
                } else {
                    out.println("<td valign=\"top\" colspan=\"5\"><em>disabled</em></td>");
                }
                out.println("</tr>");
            }
            out.println("</table>");
        } else {
            // TODO put generated comparison here
        }
        out.println("</td>");
    }

    private static String printScore(double d) {
        DecimalFormat df = new DecimalFormat("0.00");
        if (d > 0.5) {
            return "<td class=\"pass\" valign=\"top\">" + df.format(d) + "</td>";
        }
        return "<td class=\"fail\" valign=\"top\">" + df.format(d) + "</td>";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
