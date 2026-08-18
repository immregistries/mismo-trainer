package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.immregistries.mismo.trainer.model.User;
import org.immregistries.mismo.match.matchers.SimilarMatchNode;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.trainer.random.Transformer;

/**
 * This was the original servlet that demonstrated how the matching worked for
 * one example.
 * 
 * @author Nathan Bunker
 * 
 */
public class MatchNodeServlet extends HomeServlet {

    public static final String PARAM_FIELD_1 = "field1";
    public static final String PARAM_FIELD_2 = "field2";

    private class ScoreResult {
        private String field1;
        private String field2;
        private double score;

        public ScoreResult(String field1, String field2, double score) {
            this.field1 = field1;
            this.field2 = field2;
            this.score = score;
        }

        public String getField1() {
            return field1;
        }

        public String getField2() {
            return field2;
        }

        public double getScore() {
            return score;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setup(req, resp);
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        HttpSession session = req.getSession(true);
        User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);

        try {

            HomeServlet.doHeader(out, user, null);
            out.println("    <h1>Match Node Demonstration</h1>");
            // SimilarMatchNode
            String field1 = req.getParameter(PARAM_FIELD_1);
            String field2 = req.getParameter(PARAM_FIELD_2);
            if (field1 != null && field2 != null) {
                SimilarMatchNode similarMatchNode = new SimilarMatchNode("Test Similar Match Node", 0.0, 1.0,
                        "field");
                Patient patientA = new Patient("field=" + field1);
                Patient patientB = new Patient("field=" + field2);
                out.println("Match Node: " + similarMatchNode.getMatchName() + "<br/>");
                out.println("Field 1: " + field1 + "<br/>");
                out.println("Field 2: " + field2 + "<br/>");
                out.println("Score: " + similarMatchNode.score(patientA, patientB) + "<br/>");
            }
            out.println("    <form action=\"MatchNodeServlet\" method=\"POST\"> ");
            out.println("    Field 1: <input type=\"text\" name=\"" + PARAM_FIELD_1 + "\" size=\"50\"/>");
            out.println("    Field 2: <input type=\"text\" name=\"" + PARAM_FIELD_2 + "\" size=\"50\"/>");
            out.println("    <input type=\"submit\" name=\"action\" value=\"Match\"/><br/>");
            out.println("    </form>");

            out.println("  <h2>Examples</h2>");
            {
                SimilarMatchNode similarMatchNode = new SimilarMatchNode("Test Similar Match Node", 0.0, 1.0,
                        "field");

                final int listSize = 20;
                List<List<ScoreResult>> resultListList = new ArrayList<>();
                for (int i = 0; i < listSize; i++) {
                    List<ScoreResult> resultList = new ArrayList<>();
                    resultListList.add(resultList);
                }
                Transformer transformer = new Transformer();
                for (int i = 0; i < 10000; i++) {
                    String name1 = transformer.getValue(Transformer.GIRL);
                    String name2 = transformer.getValue(Transformer.GIRL);
                    if (name1.equals(name2)) {
                        continue;
                    }
                    double score = similarMatchNode.score(new Patient("field=" + name1), new Patient("field=" + name2));
                    int pos = (int) (score * listSize);
                    // only add if there are not 10 examples
                    if (resultListList.get(pos).size() >= 10) {
                        continue;
                    }
                    resultListList.get(pos).add(new ScoreResult(name1, name2, score));
                }
                for (int i = 0; i < listSize; i++) {
                    List<ScoreResult> resultList = resultListList.get(i);
                    if (resultList.size() > 0) {
                        out.println("<h3>Score < " + ((int) ((((double) (i + 1)) / listSize) * 100)) + "</h3>");
                        out.println("<table border=\"1\">");
                        out.println("<tr><th>Field 1</th><th>Field 2</th><th>Score</th></tr>");
                        for (ScoreResult scoreResult : resultList) {
                            out.println("<tr>");
                            out.println("<td>" + scoreResult.getField1() + "</td>");
                            out.println("<td>" + scoreResult.getField2() + "</td>");
                            out.println("<td>" + scoreResult.getScore() + "</td>");
                            out.println("</tr>");
                        }
                        out.println("</table>");
                    }
                }
            }

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
