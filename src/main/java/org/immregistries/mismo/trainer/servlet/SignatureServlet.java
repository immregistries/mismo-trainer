package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.User;

/**
 * This was the original servlet that demonstrated how the matching worked for
 * one example.
 * 
 * @author Nathan Bunker
 * 
 */
public class SignatureServlet extends HomeServlet {

    private static final Logger LOGGER = Logger.getLogger(SignatureServlet.class.getName());

    public static final String PARAM_COMPARE_SIGNATURES = "compareSignatures";
    /** Not {@code configurationId}: that would also change the session's loaded configuration. */
    public static final String PARAM_DECODE_CONFIGURATION_ID = "decodeConfigurationId";
    public static final String ACTION_PREVIEW = "Preview";
    public static final String ACTION_DOWNLOAD_CSV = "Download CSV";

    private static final int PREVIEW_ROW_LIMIT = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setup(req, resp);
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        HttpSession session = req.getSession(true);
        User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
        Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
        if (user != null && ACTION_DOWNLOAD_CSV.equals(req.getParameter(PARAM_ACTION))) {
            Comparison comparison = new Comparison(req, dataSession, user);
            if (comparison.rows != null) {
                try {
                    String fileName = "signatures_" + comparison.configuration.getWorldName() + "_"
                            + comparison.configuration.getIslandName() + ".csv";
                    resp.setContentType("text/csv");
                    resp.setCharacterEncoding("UTF-8");
                    resp.setHeader("Content-Disposition",
                            "attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]+", "_") + "\"");
                    PrintWriter csvOut = new PrintWriter(
                            new OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8));
                    SignatureMatrix.writeCsv(csvOut, comparison.configuration, comparison.columns,
                            comparison.rows);
                    csvOut.flush();
                } finally {
                    teardown(req, resp);
                }
                return;
            }
            // Nothing to download (no signatures, unknown hash): fall through and show the page,
            // which explains the problem.
        }
        try {

            PatientCompare patientCompare = (PatientCompare) session.getAttribute(ATTRIBUTE_PATIENT_COMPARE);
            if (patientCompare == null) {
                HomeServlet.doHeader(out, req, user, null);
                out.println("    <div class=\"aira-container--wide aira-stack\">");
                out.println("    <h1 class=\"aira-page-title\">Signature</h1>");
                out.println("    <div class=\"aira-alert aira-alert--warning\" role=\"alert\"><p>No configuration is"
                        + " currently loaded. <a href=\"WeightSetServlet\">Select a Configuration</a> first, then"
                        + " return to the Signature Inspector.</p></div>");
                out.println("    </div>");
                HomeServlet.doFooter(out, req);
                return;
            }

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
                    + HomeServlet.escapeHtml(signature) + "\" size=\"50\"/>");
            out.println("    <input type=\"submit\" name=\"action\" value=\"View\"/><br/>");
            out.println("    <label><input type=\"checkbox\" name=\"showAllFields\" value=\"true\""
                    + (showAllFields ? " checked" : "") + " onchange=\"this.form.submit()\"/>"
                    + " Show all fields (including zero-score/disabled)</label>");

            if (!signature.equals("")) {
                out.println("  <p>Signature: <strong>" + HomeServlet.escapeHtml(signature) + "</strong>: ");
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
            if (user != null) {
                printCompareSection(out, new Comparison(req, dataSession, user), dataSession, user);
            }
            out.println("    </div>");

            HomeServlet.doFooter(out, req);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to render the Signature Inspector", e);
            if (!resp.isCommitted()) {
                resp.resetBuffer();
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Unable to display the Signature Inspector.");
            }
        } finally {
            teardown(req, resp);
            out.close();
        }
    }

    /**
     * The pasted signatures for Compare Signatures, decoded. The configuration is the one picked in
     * the form, or else the one whose hash most of the signatures carry.
     */
    private static final class Comparison {
        final String text;
        final String pickedId;
        final List<SignatureMatrix.Line> lines;
        final Map<String, Integer> hashes;
        Configuration configuration;
        List<SignatureMatrix.Column> columns;
        List<SignatureMatrix.Row> rows;
        String problem;

        Comparison(HttpServletRequest req, Session dataSession, User user) {
            text = req.getParameter(PARAM_COMPARE_SIGNATURES);
            pickedId = req.getParameter(PARAM_DECODE_CONFIGURATION_ID);
            lines = SignatureMatrix.parseLines(text);
            hashes = SignatureMatrix.countHashes(lines);
            if (text == null) {
                return;
            }
            if (lines.isEmpty()) {
                problem = "No signatures found. Paste one per line.";
                return;
            }
            if (pickedId != null && !pickedId.isEmpty()) {
                configuration = OrgScope.loadConfiguration(dataSession, Integer.parseInt(pickedId), user);
                if (configuration == null) {
                    problem = "That configuration was not found.";
                    return;
                }
            } else {
                String hash = hashes.keySet().iterator().next();
                configuration = OrgScope.findConfigurationByHash(dataSession, hash, user);
                if (configuration == null) {
                    problem = "No saved configuration has the hash " + hash + ", so these signatures can't be"
                            + " read safely. Load that configuration on the Select Configuration page, or pick"
                            + " one under Decode against to read them anyway.";
                    return;
                }
            }
            PatientCompare patientCompare = new PatientCompare(configuration.getConfigurationScript());
            columns = SignatureMatrix.columns(patientCompare);
            rows = SignatureMatrix.decode(configuration, patientCompare, lines);
        }
    }

    private static void printCompareSection(PrintWriter out, Comparison comparison, Session dataSession,
            User user) {
        out.println("    <section class=\"aira-panel\" id=\"compare\">");
        out.println("      <h2 class=\"aira-panel__title\">Compare Signatures</h2>");
        out.println("      <p>Paste signatures, one per line. A count after each one (<code>signature, 412</code>)"
                + " is optional. Each signature becomes a row, with one column per node holding that node's"
                + " score from the signature, ready to open in Excel.</p>");
        out.println("      <form class=\"aira-stack\" action=\"SignatureServlet#compare\" method=\"POST\">");
        out.println("        <label class=\"aira-label\" for=\"compare-signatures\">Signatures</label>");
        out.println("        <textarea class=\"aira-textarea signature-compare-input\" id=\"compare-signatures\""
                + " name=\"" + PARAM_COMPARE_SIGNATURES + "\" rows=\"8\" wrap=\"off\">"
                + escapeHtml(comparison.text == null ? "" : comparison.text) + "</textarea>");
        out.println("        <label class=\"aira-label\" for=\"decode-configuration\">Decode against</label>");
        out.println("        <select class=\"aira-select\" id=\"decode-configuration\" name=\""
                + PARAM_DECODE_CONFIGURATION_ID + "\">");
        out.println("          <option value=\"\">The configuration matching the signatures' hash</option>");
        for (Configuration configuration : OrgScope.listConfigurations(dataSession, user)) {
            boolean selected = String.valueOf(configuration.getConfigurationId()).equals(comparison.pickedId);
            out.println("          <option value=\"" + configuration.getConfigurationId() + "\""
                    + (selected ? " selected" : "") + ">" + escapeHtml(configuration.getWorldName()) + " / "
                    + escapeHtml(configuration.getIslandName()) + " (gen " + configuration.getGeneration() + ", "
                    + escapeHtml(configuration.getHashForSignature()) + ")</option>");
        }
        out.println("        </select>");
        out.println("        <p>");
        out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\""
                + PARAM_ACTION + "\" value=\"" + ACTION_PREVIEW + "\">" + ACTION_PREVIEW + "</button>");
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\" name=\""
                + PARAM_ACTION + "\" value=\"" + ACTION_DOWNLOAD_CSV + "\">" + ACTION_DOWNLOAD_CSV + "</button>");
        out.println("        </p>");
        out.println("      </form>");

        if (comparison.problem != null) {
            out.println("      <div class=\"aira-alert aira-alert--warning\" role=\"alert\"><p>"
                    + escapeHtml(comparison.problem) + "</p></div>");
        }
        if (comparison.rows == null) {
            out.println("    </section>");
            return;
        }
        Configuration configuration = comparison.configuration;
        int rowCount = comparison.rows.size();
        out.println("      <p><strong>" + rowCount + " signature" + (rowCount == 1 ? "" : "s") + "</strong> decoded with"
                + " <strong>" + escapeHtml(configuration.getWorldName()) + " / "
                + escapeHtml(configuration.getIslandName()) + "</strong> (<code>"
                + escapeHtml(configuration.getHashForSignature()) + "</code>), " + comparison.columns.size()
                + " nodes.</p>");
        int otherHashes = 0;
        for (Map.Entry<String, Integer> entry : comparison.hashes.entrySet()) {
            if (!entry.getKey().equals(configuration.getHashForSignature())) {
                otherHashes += entry.getValue();
            }
        }
        if (otherHashes > 0) {
            out.println("      <div class=\"aira-alert aira-alert--warning\" role=\"alert\"><p>" + otherHashes
                    + " of these signatures were made by a different configuration (a different hash). Their"
                    + " values are probably wrong when read this way; see the Note column.</p></div>");
        }
        boolean showCount = SignatureMatrix.anyCount(comparison.rows);
        boolean showNote = SignatureMatrix.anyNote(comparison.rows);
        out.println("      <div class=\"aira-table-wrap signature-compare-table\">");
        out.println("      <table class=\"aira-table\">");
        out.println("        <thead>");
        out.print("          <tr><th>Signature</th>" + (showCount ? "<th>Count</th>" : "")
                + "<th>Classification</th>" + (showNote ? "<th>Note</th>" : ""));
        for (SignatureMatrix.Column column : comparison.columns) {
            out.print("<th title=\"" + escapeHtml(column.section()) + "\">" + escapeHtml(column.name()) + "</th>");
        }
        out.println("</tr>");
        out.println("        </thead>");
        out.println("        <tbody>");
        for (SignatureMatrix.Row row : comparison.rows.subList(0, Math.min(rowCount, PREVIEW_ROW_LIMIT))) {
            out.print("          <tr><td class=\"aira-table__cell--code aira-table__cell--nowrap\">"
                    + escapeHtml(row.line().signature()) + "</td>");
            if (showCount) {
                out.print("<td class=\"aira-table__cell--numeric\">"
                        + (row.line().count() == null ? "" : row.line().count()) + "</td>");
            }
            out.print("<td>" + escapeHtml(row.classification()) + "</td>");
            if (showNote) {
                out.print("<td>" + escapeHtml(row.note()) + "</td>");
            }
            for (Double score : row.scores()) {
                boolean nonZero = score != null && score != 0.0;
                out.print("<td class=\"aira-table__cell--numeric" + (nonZero ? " signature-compare-nonzero" : "")
                        + "\">" + SignatureMatrix.formatScore(score) + "</td>");
            }
            out.println("</tr>");
        }
        out.println("        </tbody>");
        out.println("      </table>");
        out.println("      </div>");
        if (rowCount > PREVIEW_ROW_LIMIT) {
            out.println("      <p class=\"aira-muted\">Showing the first " + PREVIEW_ROW_LIMIT + " of " + rowCount
                    + ". The CSV has all of them.</p>");
        }
        out.println("    </section>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}
