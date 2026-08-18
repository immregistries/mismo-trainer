package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.trainer.model.User;

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
            boolean showAllFields = "true".equals(req.getParameter("showAllFields"));

            HomeServlet.doHeader(out, req, user, null);
            out.println("    <div class=\"aira-container--wide aira-stack\">");
            out.println("    <h1 class=\"aira-page-title\">Signature</h1>");
            out.println("    <form action=\"SignatureServlet\" method=\"POST\"> ");
            out.println("    Signature: <input type=\"text\" name=\"signature\" value=\""
                    + signature + "\" size=\"50\"/>");
            out.println("    <input type=\"submit\" name=\"action\" value=\"View\"/><br/>");
            out.println("    <label><input type=\"checkbox\" name=\"showAllFields\" value=\"true\""
                    + (showAllFields ? " checked" : "") + " onchange=\"this.form.submit()\"/>"
                    + " Show all fields (including zero-score/disabled)</label>");

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
                MatchTreeRenderer.printAggregateNodeFromSignature(out, patientCompare.getMatch(), "match",
                        showAllFields, true);
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Not a Match</td>");
                MatchTreeRenderer.printAggregateNodeFromSignature(out, patientCompare.getNotMatch(), "notmatch",
                        showAllFields, false);
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Twin</td>");
                MatchTreeRenderer.printAggregateNodeFromSignature(out, patientCompare.getTwin(), "twin",
                        showAllFields, false);
                out.println("    </tr>");
                out.println("<tr><td valign=\"top\">Missing</td>");
                MatchTreeRenderer.printAggregateNodeFromSignature(out, patientCompare.getMissing(), "missing",
                        showAllFields, true);
                out.println("    </tr>");
                out.println("    </table>");
            }
            out.println("    </form>");
            out.println("    </div>");

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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
