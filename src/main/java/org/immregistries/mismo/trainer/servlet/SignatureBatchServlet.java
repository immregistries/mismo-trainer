package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.SignatureBatch;
import org.immregistries.mismo.trainer.model.SignatureBatchEntry;
import org.immregistries.mismo.trainer.model.User;

/**
 * Batch Signature Analysis (v2-roadmap.md §12; database-changes-for-functional-model.md §7;
 * proposed-functional-model-and-navigation.md §9.3): upload a collection of raw
 * {@code (signature, count)} pairs, then decode them on demand against any configuration the
 * analyst chooses -- decoding is never persisted, so the same batch can be re-analyzed against a
 * different configuration at any time without re-uploading. Decoding reuses
 * {@code PatientCompare.setSignature} (the same decode {@code SignatureServlet}'s Signature
 * Inspector uses) plus {@link SignatureClassifier} for the resulting classification.
 */
@MultipartConfig
public class SignatureBatchServlet extends HomeServlet {

  public static final String PARAM_SIGNATURE_BATCH_ID = "signatureBatchId";
  public static final String PARAM_LABEL = "label";
  public static final String PARAM_SIGNATURES_TEXT = "signaturesText";
  public static final String PARAM_SIGNATURE_FILE = "signatureFile";
  public static final String PARAM_CONFIGURATION_ID = HomeServlet.PARAM_CONFIGURATION_ID;
  public static final String PARAM_SORT = "sort";
  public static final String PARAM_FILTER_CLASSIFICATION = "filterClassification";
  public static final String PARAM_GROUP = "group";

  public static final String ACTION_UPLOAD = "Upload Batch";
  public static final String ACTION_EXPORT = "Export";

  private static final String SORT_COUNT_DESC = "countDesc";
  private static final String SORT_COUNT_ASC = "countAsc";
  private static final String SORT_CLASSIFICATION = "classification";
  private static final String FILTER_ALL = "All";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }
      Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);

      SignatureBatch batchSelected = null;
      if (req.getParameter(PARAM_SIGNATURE_BATCH_ID) != null) {
        batchSelected = OrgScope.loadSignatureBatch(dataSession,
            Integer.parseInt(req.getParameter(PARAM_SIGNATURE_BATCH_ID)), user);
      }

      if (batchSelected != null && ACTION_EXPORT.equals(req.getParameter(PARAM_ACTION))) {
        exportBatch(req, resp, dataSession, batchSelected, user);
        return;
      }

      resp.setContentType("text/html");
      PrintWriter out = new PrintWriter(resp.getOutputStream());
      try {
        try {
          HomeServlet.doHeader(out, req, user, req.getParameter(PARAM_MESSAGE));
          out.println("    <div class=\"aira-container--wide aira-stack\">");
          out.println("    <h1 class=\"aira-page-title\">Signature Batch Analysis</h1>");

          printUploadForm(out);

          out.println("    <section class=\"aira-panel\">");
          out.println("      <h2 class=\"aira-panel__title\">Batches</h2>");
          List<SignatureBatch> batches = OrgScope.listSignatureBatches(dataSession, user);
          printBatchList(out, batches, batchSelected);
          out.println("    </section>");

          if (batchSelected != null) {
            printAnalysis(out, req, dataSession, user, batchSelected);
          }

          out.println("    </div>");
          HomeServlet.doFooter(out, req);
        } catch (Exception e) {
          out.println("<pre>");
          e.printStackTrace(out);
          out.println("</pre>");
        }
      } finally {
        out.close();
      }
    } finally {
      teardown(req, resp);
    }
  }

  private void printUploadForm(PrintWriter out) {
    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Upload a Signature Batch</h2>");
    out.println("      <p>Paste or upload a list of <code>Signature, Count</code> pairs, one per line -- an"
        + " optional header row is skipped automatically.</p>");
    out.println("      <form action=\"SignatureBatchServlet\" method=\"POST\" enctype=\"multipart/form-data\">");
    out.println("        <table>");
    out.println("          <tr><td valign=\"top\">Label</td><td><input type=\"text\" name=\"" + PARAM_LABEL
        + "\" size=\"40\"/></td></tr>");
    out.println("          <tr><td valign=\"top\">Paste</td><td><textarea name=\"" + PARAM_SIGNATURES_TEXT
        + "\" cols=\"60\" rows=\"8\" placeholder=\"Signature-A, 18492&#10;Signature-B, 7210\"></textarea></td></tr>");
    out.println("          <tr><td valign=\"top\">Or File</td><td><input type=\"file\" name=\""
        + PARAM_SIGNATURE_FILE + "\"/></td></tr>");
    out.println("          <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
        + "\" value=\"" + ACTION_UPLOAD + "\"/></td></tr>");
    out.println("        </table>");
    out.println("      </form>");
    out.println("    </section>");
  }

  private void printBatchList(PrintWriter out, List<SignatureBatch> batches, SignatureBatch batchSelected) {
    if (batches.isEmpty()) {
      out.println("      <p>No signature batches uploaded yet.</p>");
      return;
    }
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Uploaded</th><th>Label</th><th>Uploaded By</th><th>&nbsp;</th></tr>");
    for (SignatureBatch batch : batches) {
      boolean selected = batchSelected != null && batchSelected.getSignatureBatchId() == batch.getSignatureBatchId();
      out.println("        <tr" + (selected ? " class=\"pass\"" : "") + ">");
      out.println("          <td>" + sdf.format(batch.getUploadedAt()) + "</td>");
      out.println("          <td>" + (batch.getLabel() == null ? "&nbsp;" : escapeHtml(batch.getLabel())) + "</td>");
      out.println("          <td>" + (batch.getUploadedByUser() == null ? "" : escapeHtml(
          batch.getUploadedByUser().getDisplayName() != null ? batch.getUploadedByUser().getDisplayName()
              : batch.getUploadedByUser().getEmail())) + "</td>");
      out.println("          <td><a href=\"SignatureBatchServlet?" + PARAM_SIGNATURE_BATCH_ID + "="
          + batch.getSignatureBatchId() + "\">" + (selected ? "Viewing" : "View") + "</a></td>");
      out.println("        </tr>");
    }
    out.println("      </table>");
  }

  private void printAnalysis(PrintWriter out, HttpServletRequest req, Session dataSession, User user,
      SignatureBatch batch) {
    List<SignatureBatchEntry> entries = OrgScope.listSignatureBatchEntries(dataSession, batch);

    Configuration configurationSelected = resolveSelectedConfiguration(req, dataSession, user);
    String sort = req.getParameter(PARAM_SORT) == null ? SORT_COUNT_DESC : req.getParameter(PARAM_SORT);
    String filterClassification = req.getParameter(PARAM_FILTER_CLASSIFICATION);
    boolean group = "true".equals(req.getParameter(PARAM_GROUP));

    out.println("    <section class=\"aira-panel\">");
    out.println("      <h2 class=\"aira-panel__title\">Analyze &quot;"
        + (batch.getLabel() == null ? ("Batch #" + batch.getSignatureBatchId()) : escapeHtml(batch.getLabel()))
        + "&quot;</h2>");

    out.println("      <form action=\"SignatureBatchServlet\" method=\"GET\">");
    out.println("        <input type=\"hidden\" name=\"" + PARAM_SIGNATURE_BATCH_ID + "\" value=\""
        + batch.getSignatureBatchId() + "\"/>");
    out.println("        <table>");
    out.println("          <tr><td valign=\"top\">Decode Against</td><td><select name=\"" + PARAM_CONFIGURATION_ID
        + "\">");
    out.println("            <option value=\"\">(raw -- no decode)</option>");
    for (Configuration configuration : OrgScope.listConfigurations(dataSession, user)) {
      boolean selected = configurationSelected != null
          && configurationSelected.getConfigurationId() == configuration.getConfigurationId();
      out.println("            <option value=\"" + configuration.getConfigurationId() + "\""
          + (selected ? " selected=\"true\"" : "") + ">" + escapeHtml(configuration.getWorldName()) + " / "
          + escapeHtml(configuration.getIslandName()) + " (gen " + configuration.getGeneration() + ")</option>");
    }
    out.println("          </select></td></tr>");
    out.println("          <tr><td valign=\"top\">Sort</td><td><select name=\"" + PARAM_SORT + "\">");
    printSortOption(out, SORT_COUNT_DESC, "Frequency (high to low)", sort);
    printSortOption(out, SORT_COUNT_ASC, "Frequency (low to high)", sort);
    if (configurationSelected != null) {
      printSortOption(out, SORT_CLASSIFICATION, "Classification", sort);
    }
    out.println("          </select></td></tr>");
    if (configurationSelected != null) {
      out.println("          <tr><td valign=\"top\">Filter</td><td><select name=\"" + PARAM_FILTER_CLASSIFICATION
          + "\">");
      printFilterOption(out, FILTER_ALL, filterClassification);
      printFilterOption(out, org.immregistries.mismo.match.model.MatchItem.MATCH, filterClassification);
      printFilterOption(out, org.immregistries.mismo.match.model.MatchItem.POSSIBLE_MATCH, filterClassification);
      printFilterOption(out, org.immregistries.mismo.match.model.MatchItem.NOT_A_MATCH, filterClassification);
      out.println("          </select></td></tr>");
      out.println("          <tr><td valign=\"top\">Group</td><td><label><input type=\"checkbox\" name=\""
          + PARAM_GROUP + "\" value=\"true\"" + (group ? " checked" : "")
          + "/> Group by classification</label></td></tr>");
    }
    out.println("          <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" value=\"Apply\"/></td></tr>");
    out.println("        </table>");
    out.println("      </form>");

    List<DecodedEntry> decoded = decode(entries, configurationSelected);
    if (configurationSelected != null && filterClassification != null && !FILTER_ALL.equals(filterClassification)) {
      List<DecodedEntry> filtered = new ArrayList<DecodedEntry>();
      for (DecodedEntry d : decoded) {
        if (filterClassification.equals(d.classification)) {
          filtered.add(d);
        }
      }
      decoded = filtered;
    }
    sortDecoded(decoded, sort, configurationSelected != null);

    String exportUrl = "SignatureBatchServlet?" + PARAM_SIGNATURE_BATCH_ID + "=" + batch.getSignatureBatchId()
        + "&" + PARAM_ACTION + "=" + ACTION_EXPORT
        + (configurationSelected != null ? "&" + PARAM_CONFIGURATION_ID + "=" + configurationSelected.getConfigurationId() : "")
        + "&" + PARAM_SORT + "=" + sort
        + (filterClassification != null ? "&" + PARAM_FILTER_CLASSIFICATION + "=" + filterClassification : "");
    out.println("      <p><a href=\"" + exportUrl + "\">Export to delimited file (" + decoded.size()
        + " rows)</a></p>");

    if (group && configurationSelected != null) {
      printGrouped(out, decoded);
    } else {
      printEntryTable(out, decoded, configurationSelected != null);
    }
    out.println("    </section>");
  }

  private void printSortOption(PrintWriter out, String value, String label, String current) {
    out.println("            <option value=\"" + value + "\"" + (value.equals(current) ? " selected=\"true\"" : "")
        + ">" + label + "</option>");
  }

  private void printFilterOption(PrintWriter out, String value, String current) {
    out.println("            <option value=\"" + value + "\"" + (value.equals(current) ? " selected=\"true\"" : "")
        + ">" + value + "</option>");
  }

  /** One batch entry paired with its on-demand-decoded classification (or {@code null} if undecoded). */
  private static final class DecodedEntry {
    final SignatureBatchEntry entry;
    final String classification;

    DecodedEntry(SignatureBatchEntry entry, String classification) {
      this.entry = entry;
      this.classification = classification;
    }
  }

  private List<DecodedEntry> decode(List<SignatureBatchEntry> entries, Configuration configuration) {
    List<DecodedEntry> decoded = new ArrayList<DecodedEntry>();
    PatientCompare patientCompare = configuration == null ? null
        : new PatientCompare(configuration.getConfigurationScript());
    for (SignatureBatchEntry entry : entries) {
      String classification = null;
      if (patientCompare != null) {
        patientCompare.setSignature(entry.getSignature());
        classification = SignatureClassifier.classify(patientCompare);
      }
      decoded.add(new DecodedEntry(entry, classification));
    }
    return decoded;
  }

  private void sortDecoded(List<DecodedEntry> decoded, String sort, boolean decodedAvailable) {
    if (SORT_COUNT_ASC.equals(sort)) {
      decoded.sort(Comparator.comparingInt(d -> d.entry.getCount()));
    } else if (SORT_CLASSIFICATION.equals(sort) && decodedAvailable) {
      decoded.sort(Comparator.comparing((DecodedEntry d) -> d.classification == null ? "" : d.classification)
          .thenComparing(d -> -d.entry.getCount()));
    } else {
      decoded.sort((a, b) -> Integer.compare(b.entry.getCount(), a.entry.getCount()));
    }
  }

  private void printGrouped(PrintWriter out, List<DecodedEntry> decoded) {
    Map<String, List<DecodedEntry>> byClassification = new LinkedHashMap<String, List<DecodedEntry>>();
    for (DecodedEntry d : decoded) {
      String key = d.classification == null ? "(undecoded)" : d.classification;
      byClassification.computeIfAbsent(key, k -> new ArrayList<DecodedEntry>()).add(d);
    }
    for (Map.Entry<String, List<DecodedEntry>> group : byClassification.entrySet()) {
      long totalCount = 0;
      for (DecodedEntry d : group.getValue()) {
        totalCount += d.entry.getCount();
      }
      out.println("      <h3>" + escapeHtml(group.getKey()) + " (" + group.getValue().size() + " signatures, "
          + totalCount + " total occurrences)</h3>");
      printEntryTable(out, group.getValue(), false);
    }
  }

  private void printEntryTable(PrintWriter out, List<DecodedEntry> decoded, boolean showClassification) {
    if (decoded.isEmpty()) {
      out.println("      <p>No entries match the current filter.</p>");
      return;
    }
    out.println("      <table border=\"1\" cellspacing=\"0\">");
    out.println("        <tr><th>Signature</th><th>Count</th>" + (showClassification ? "<th>Classification</th>"
        : "") + "</tr>");
    for (DecodedEntry d : decoded) {
      out.println("        <tr>");
      out.println("          <td>" + escapeHtml(d.entry.getSignature()) + "</td>");
      out.println("          <td>" + d.entry.getCount() + "</td>");
      if (showClassification) {
        out.println("          <td>" + escapeHtml(d.classification) + "</td>");
      }
      out.println("        </tr>");
    }
    out.println("      </table>");
  }

  /**
   * Defaults the configuration selector to whatever configuration is currently loaded in the
   * analyst's session ({@code HomeServlet}'s "Configuration Loaded" convention -- {@link
   * #ATTRIBUTE_PATIENT_COMPARE}), unless the request explicitly picked one (including explicitly
   * picking "none" via an empty value).
   */
  private Configuration resolveSelectedConfiguration(HttpServletRequest req, Session dataSession, User user) {
    String param = req.getParameter(PARAM_CONFIGURATION_ID);
    if (param != null) {
      if (param.isEmpty()) {
        return null;
      }
      return OrgScope.loadConfiguration(dataSession, Integer.parseInt(param), user);
    }
    PatientCompare loaded = (PatientCompare) req.getSession().getAttribute(ATTRIBUTE_PATIENT_COMPARE);
    if (loaded == null || loaded.getConfiguration() == null) {
      return null;
    }
    return OrgScope.findConfigurationByHash(dataSession, loaded.getConfiguration().getHashForSignature(), user);
  }

  private void exportBatch(HttpServletRequest req, HttpServletResponse resp, Session dataSession,
      SignatureBatch batch, User user) throws IOException {
    List<SignatureBatchEntry> entries = OrgScope.listSignatureBatchEntries(dataSession, batch);
    Configuration configurationSelected = resolveSelectedConfiguration(req, dataSession, user);
    String sort = req.getParameter(PARAM_SORT) == null ? SORT_COUNT_DESC : req.getParameter(PARAM_SORT);
    String filterClassification = req.getParameter(PARAM_FILTER_CLASSIFICATION);

    List<DecodedEntry> decoded = decode(entries, configurationSelected);
    if (configurationSelected != null && filterClassification != null && !FILTER_ALL.equals(filterClassification)) {
      List<DecodedEntry> filtered = new ArrayList<DecodedEntry>();
      for (DecodedEntry d : decoded) {
        if (filterClassification.equals(d.classification)) {
          filtered.add(d);
        }
      }
      decoded = filtered;
    }
    sortDecoded(decoded, sort, configurationSelected != null);

    resp.setContentType("text/csv");
    resp.setCharacterEncoding("UTF-8");
    resp.setHeader("Content-Disposition", "attachment; filename=\"signature_batch_"
        + batch.getSignatureBatchId() + ".csv\"");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      out.println(configurationSelected != null ? "Signature,Count,Classification" : "Signature,Count");
      for (DecodedEntry d : decoded) {
        out.print(csv(d.entry.getSignature()));
        out.print(",");
        out.print(d.entry.getCount());
        if (configurationSelected != null) {
          out.print(",");
          out.print(csv(d.classification));
        }
        out.println();
      }
    } finally {
      out.close();
    }
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(ATTRIBUTE_USER);
    Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);

    String message;
    if (user == null) {
      message = "You must be logged in.";
    } else if (!ACTION_UPLOAD.equals(req.getParameter(PARAM_ACTION))) {
      message = "Unknown action.";
    } else {
      String label = req.getParameter(PARAM_LABEL);
      List<SignatureBatchEntry> parsedEntries = parseUploadedEntries(req);
      if (parsedEntries.isEmpty()) {
        message = "No Signature, Count pairs found -- paste text or choose a file first.";
      } else {
        Transaction transaction = dataSession.beginTransaction();
        SignatureBatch batch = OrgScope.createSignatureBatch(dataSession,
            (label == null || label.isBlank()) ? null : label, parsedEntries, user);
        transaction.commit();
        teardown(req, resp);
        resp.sendRedirect("SignatureBatchServlet?" + PARAM_SIGNATURE_BATCH_ID + "=" + batch.getSignatureBatchId()
            + "&" + PARAM_MESSAGE + "=" + URLEncoder.encode(
                parsedEntries.size() + " signature entries uploaded.", "UTF-8"));
        return;
      }
    }
    teardown(req, resp);
    resp.sendRedirect("SignatureBatchServlet?" + PARAM_MESSAGE + "=" + URLEncoder.encode(message, "UTF-8"));
  }

  private List<SignatureBatchEntry> parseUploadedEntries(HttpServletRequest req)
      throws IOException, ServletException {
    Part filePart = req.getPart(PARAM_SIGNATURE_FILE);
    if (filePart != null && filePart.getSize() > 0) {
      try (BufferedReader in = new BufferedReader(
          new InputStreamReader(filePart.getInputStream(), StandardCharsets.UTF_8))) {
        return parseEntries(in);
      }
    }
    String pasted = req.getParameter(PARAM_SIGNATURES_TEXT);
    if (pasted != null && !pasted.isBlank()) {
      try (BufferedReader in = new BufferedReader(new StringReader(pasted))) {
        return parseEntries(in);
      }
    }
    return new ArrayList<SignatureBatchEntry>();
  }

  /**
   * Parses the {@code Signature, Count} format from proposed-functional-model-and-navigation.md
   * §9.3, one pair per line. An optional header line (first token literally "Signature") is
   * skipped; malformed lines (no comma, non-numeric count) are silently skipped rather than
   * failing the whole upload.
   */
  static List<SignatureBatchEntry> parseEntries(BufferedReader in) throws IOException {
    List<SignatureBatchEntry> entries = new ArrayList<SignatureBatchEntry>();
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      int comma = line.indexOf(',');
      if (comma < 0) {
        continue;
      }
      String signature = line.substring(0, comma).trim();
      String countText = line.substring(comma + 1).trim();
      if (signature.equalsIgnoreCase("Signature")) {
        continue;
      }
      int count;
      try {
        count = Integer.parseInt(countText);
      } catch (NumberFormatException e) {
        continue;
      }
      SignatureBatchEntry entry = new SignatureBatchEntry();
      entry.setSignature(signature);
      entry.setCount(count);
      entries.add(entry);
    }
    return entries;
  }
}
