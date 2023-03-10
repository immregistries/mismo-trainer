package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.random.Transformer;
import org.immregistries.mismo.trainer.random.Typest;

/**
 * Creates a list of patient records, some that match and some that don't, and
 * places them in a script with a suggested match criteria. This allows for
 * rapid creation of test data.
 * 
 * @author Nathan Bunker
 * 
 */
public class RandomForCDCServlet extends HomeServlet
{

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    response.setContentType("text/html");
    PrintWriter out = new PrintWriter(response.getOutputStream());
    Random random = new Random();
    HttpSession session = request.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
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
      out.println("        if (vis.display == '' && elem.offsetWidth != undefined && elem.offsetHeight != undefined) ");
      out.println("          vis.display = (elem.offsetWidth != 0 && elem.offsetHeight != 0) ? 'block' : 'none';");
      out.println("        vis.display = (vis.display == '' || vis.display == 'block') ? 'none' : 'block';");
      out.println("      }");
      out.println("    </script>");
      out.println("    <h1>Random Patient Scripts</h1>");

      out.println("    <h2>Sensitivity Cases</h2>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr>");
      out.println("        <th>ID</th>");
      out.println("        <th>1st Challenge Category</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Field</th>");
      out.println("        <th>2nd Challenge Category</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Field</th>");
      out.println("        <th>3rd Challenge Category</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Field</th>");
      out.println("        <th>4th Challenge Category</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Field</th>");
      out.println("        <th>5th Challenge Category</th>");
      out.println("        <th>Type</th>");
      out.println("        <th>Field</th>");
      out.println("        <th>Expected Result</th>");
      out.println("        <th>Actual Result</th>");
      out.println("        <th>Pass/Fail</th>");
      out.println("        <th>Record Type</th>");
      out.println("        <th>Complex Case</th>");
      out.println("        <th>Notes</th>");
      out.println("        <th>LastName</th>");
      out.println("        <th>FirstName</th>");
      out.println("        <th>MiddleName</th>");
      out.println("        <th>Alias (AKA)</th>");
      out.println("        <th>Guardian Information</th>");
      out.println("        <th>Suffix</th>");
      out.println("        <th>Birth Order (multi- births)</th>");
      out.println("        <th>Sex</th>");
      out.println("        <th>DOB</th>");
      out.println("        <th>Medicaid ID</th>");
      out.println("        <th>SSN</th>");
      out.println("        <th>Medical Record ID</th>");
      out.println("        <th>Patient Org ID</th>");
      out.println("        <th>Street Address</th>");
      out.println("        <th>City</th>");
      out.println("        <th>State</th>");
      out.println("        <th>Zip</th>");
      out.println("        <th>Telephone</th>");
      out.println("        <th>MomMaiden</th>");
      out.println("        <th>MomLast</th>");
      out.println("        <th>MomFirst</th>");
      out.println("        <th>MomMiddle</th>");
      out.println("        <th>VacName</th>");
      out.println("        <th>VacCode</th>");
      out.println("        <th>VacMfr</th>");
      out.println("        <th>VacDate</th>");
      out.println("        <th>Mitigation Strategy</th>");
      out.println("      </tr>");

      out.flush();

      StringWriter stringWriter = new StringWriter();
      PrintWriter scriptOut = new PrintWriter(stringWriter);
      int count = 0;
      for (Typest.Condition condition1 : Typest.Condition.values()) {
        if (condition1 == Typest.Condition.SHOT_HISTORY_INCOMPLETE
            || condition1 == Typest.Condition.SHOT_HISTORY_MISSING) {
          continue;
        }
        Set<Typest.Condition> conditionUsed2 = new HashSet<Typest.Condition>();
        for (int i = 0; i < 10; i++) {

          Typest.Condition condition2 = null;
          while (condition2 == null || condition2 == Typest.Condition.SHOT_HISTORY_INCOMPLETE
              || condition2 == Typest.Condition.SHOT_HISTORY_MISSING || condition1 == condition2
              || conditionUsed2.contains(condition2)) {
            condition2 = Typest.Condition.values()[random.nextInt(Typest.Condition.values().length)];
          }
          conditionUsed2.add(condition2);

          Typest.Condition condition3 = null;
          while (condition3 == null || condition3 == Typest.Condition.SHOT_HISTORY_INCOMPLETE
              || condition3 == Typest.Condition.SHOT_HISTORY_MISSING || condition1 == condition3
              || condition2 == condition3) {
            condition3 = Typest.Condition.values()[random.nextInt(Typest.Condition.values().length)];
          }

          Typest.Type typeSelected1 = Typest.Type.IDEAL;
          Typest.Type typeSelected2 = Typest.Type.values()[random.nextInt(Typest.Type.values().length)];
          count++;

          Typest.Type typeA = typeSelected1;
          Typest.Type typeB = typeSelected2;
          Typest.Type typeC = typeSelected2;
          Typest.Condition conditionB = null;
          Typest.Condition conditionC = null;
          Transformer transformer = new Transformer();
          Patient patientA = null;
          Patient patientB = null;
          Patient patientC = null;
          Patient patient = transformer.createPatient(Transformer.COMPLETE);
          if (condition1 == Typest.Condition.ALIAS_MISSING) {
            transformer.addAlias(patient);
          } else if (condition1 == Typest.Condition.SUFFIX_MISSING) {
            transformer.addSuffix(patient);
          } else if (condition1 == Typest.Condition.BIRTH_MULITPLE_MISSING_FOR_TWIN
              || condition1 == Typest.Condition.BIRTH_ORDER_MISSING_FOR_TWIN) {
            // Force Twin
            patient.setBirthOrder("2");
            patient.setBirthStatus("Y");
            patient.setBirthType("2");
          }
          Patient closeMatch = null;
          if (!patient.getBirthOrder().equals("") && !patient.getBirthOrder().equals("1")) {
            closeMatch = transformer.makeTwin(patient);
          } else {
            closeMatch = transformer.makeCloseMatch(patient);
          }
          patient.setGuardianNameFirst(patient.getMotherNameFirst());
          patient.setGuardianNameLast(patient.getMotherNameLast());
          closeMatch.setGuardianNameFirst(closeMatch.getMotherNameFirst());
          closeMatch.setGuardianNameLast(closeMatch.getMotherNameLast());
          Typest typest = new Typest(transformer);
          patientA = typest.type(patient, closeMatch, typeA, new Typest.Condition[] { condition1, condition2,
              condition3 });
          patientB = typest.type(patient, closeMatch, typeB, conditionB);
          transformer.changeMrn(patientB);
          patientC = typest.type(closeMatch, patient, typeC, conditionC);

          String expectedResultB = "Match";

          int score = 0;
          score += typeA == Typest.Type.IDEAL ? 10 : 0;
          score += typeA == Typest.Type.GREATA ? 9 : 0;
          score += typeA == Typest.Type.GREATB ? 9 : 0;
          score += typeA == Typest.Type.GOODA ? 7 : 0;
          score += typeA == Typest.Type.GOODB ? 7 : 0;
          score += typeA == Typest.Type.POOR ? 2 : 0;
          score += typeB == Typest.Type.IDEAL ? 10 : 0;
          score += typeB == Typest.Type.GREATA ? 9 : 0;
          score += typeB == Typest.Type.GREATB ? 9 : 0;
          score += typeB == Typest.Type.GOODA ? 7 : 0;
          score += typeB == Typest.Type.GOODB ? 7 : 0;
          score += typeB == Typest.Type.POOR ? 2 : 0;

          // Assume low
          int highScore = 14;
          int lowScore = 8;
          // Medium first
          if (condition1 == Typest.Condition.ADDRESS_CHANGED || condition1 == Typest.Condition.ADDRESS_TYPO
              || condition1 == Typest.Condition.ADDRESS_STREET_MISSING
              || condition1 == Typest.Condition.FIRST_NAME_CHANGED || condition1 == Typest.Condition.PHONE_CHANGED
              || condition1 == Typest.Condition.SSN_TYPO) {
            highScore = 15;
            lowScore = 10;
          }
          // High
          if (condition1 == Typest.Condition.DOB_VALUE_SWAPPED || condition1 == Typest.Condition.DOB_OFF_BY_1
              || condition1 == Typest.Condition.MEDICAID_NUM_SHARED
              || condition1 == Typest.Condition.MOTHERS_MAIDEN_NAME_CHANGED
              || condition1 == Typest.Condition.MRN_SHARED_MRN || condition1 == Typest.Condition.SSN_SHARED
              || condition1 == Typest.Condition.BIRTH_MULITPLE_MISSING_FOR_TWIN
              || condition1 == Typest.Condition.BIRTH_ORDER_MISSING_FOR_TWIN) {
            highScore = 18;
            lowScore = 10;
          }
          if (score >= highScore) {
            expectedResultB = "Match";
          } else if (score <= lowScore) {
            expectedResultB = "Possible Match";
          } else {
            expectedResultB = "Not Sure";
          }

          if (!patient.getBirthOrder().equals("") && !patient.getBirthOrder().equals("1")) {
            expectedResultB = "Not Sure";
          }

          PatientCompare patientCompareB = new PatientCompare();
          patientCompareB.setPatientA(new Patient(patientA.getValues()));
          patientCompareB.setPatientB(new Patient(patientB.getValues()));

          PatientCompare patientCompareC = new PatientCompare();
          patientCompareC.setPatientA(new Patient(patientA.getValues()));
          patientCompareC.setPatientB(new Patient(patientC.getValues()));

          out.println("      <tr>");
          out.println("        <td>" + (count * 2 - 1) + "</td>");
          out.println("        <td>" + condition1 + "</td>");
          out.println("        <td>" + condition1.getType() + "</td>");
          out.println("        <td>" + condition1.getField() + "</td>");
          out.println("        <td>" + condition2 + "</td>");
          out.println("        <td>" + condition2.getType() + "</td>");
          out.println("        <td>" + condition2.getField() + "</td>");
          out.println("        <td>" + condition3 + "</td>");
          out.println("        <td>" + condition3.getType() + "</td>");
          out.println("        <td>" + condition3.getField() + "</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>Sensitivity</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>" + patientA.getNameLast() + "</td>");
          out.println("        <td>" + patientA.getNameFirst() + "</td>");
          out.println("        <td>" + patientA.getNameMiddle() + "</td>");
          out.println("        <td>" + patientA.getNameAlias() + "</td>");
          out.println("        <td></td>");
          out.println("        <td>" + patientA.getNameSuffix() + "</td>");
          out.println("        <td>" + patientA.getBirthOrder() + "</td>");
          out.println("        <td>" + patientA.getGender() + "</td>");
          out.println("        <td>" + patientA.getBirthDate() + "</td>");
          out.println("        <td>" + patientA.getMedicaid() + "</td>");
          out.println("        <td>" + patientA.getSsn() + "</td>");
          int pos = patientA.getMrns().indexOf("-");
          out.println("        <td>" + (pos == -1 ? patientA.getMrns() : patientA.getMrns().substring(pos + 1))
              + "</td>");
          out.println("        <td>" + (pos == -1 ? "" : patientA.getMrns().substring(0, pos)) + "</td>");
          out.println("        <td>" + patientA.getAddressStreet1() + "</td>");
          out.println("        <td>" + patientA.getAddressCity() + "</td>");
          out.println("        <td>" + patientA.getAddressState() + "</td>");
          out.println("        <td>" + patientA.getAddressZip() + "</td>");
          out.println("        <td>" + patientA.getPhone() + "</td>");
          out.println("        <td>" + patientA.getMotherMaidenName() + "</td>");
          out.println("        <td>" + patientA.getMotherNameLast() + "</td>");
          out.println("        <td>" + patientA.getMotherNameFirst() + "</td>");
          out.println("        <td>" + patientA.getMotherNameMiddle() + "</td>");
          out.println("        <td>" + patientA.getVacName() + "</td>");
          out.println("        <td>" + patientA.getVacCode() + "</td>");
          out.println("        <td>" + patientA.getVacMfr() + "</td>");
          out.println("        <td>" + patientA.getVacDate() + "</td>");
          out.println("        <td></td>");
          out.println("      </tr>");
          out.println("      <tr>");
          out.println("        <td>" + (count * 2) + "</td>");
          out.println("        <td>" + condition1 + "</td>");
          out.println("        <td>" + condition1.getType() + "</td>");
          out.println("        <td>" + condition1.getField() + "</td>");
          out.println("        <td>" + condition2 + "</td>");
          out.println("        <td>" + condition2.getType() + "</td>");
          out.println("        <td>" + condition2.getField() + "</td>");
          out.println("        <td>" + condition3 + "</td>");
          out.println("        <td>" + condition3.getType() + "</td>");
          out.println("        <td>" + condition3.getField() + "</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>" + expectedResultB + "</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>Base</td>");
          out.println("        <td>X</td>");
          out.println("        <td></td>");
          out.println("        <td>" + patientB.getNameLast() + "</td>");
          out.println("        <td>" + patientB.getNameFirst() + "</td>");
          out.println("        <td>" + patientB.getNameMiddle() + "</td>");
          out.println("        <td>" + patientB.getNameAlias() + "</td>");
          out.println("        <td></td>");
          out.println("        <td>" + patientB.getNameSuffix() + "</td>");
          out.println("        <td>" + patientB.getBirthOrder() + "</td>");
          out.println("        <td>" + patientB.getGender() + "</td>");
          out.println("        <td>" + patientB.getBirthDate() + "</td>");
          out.println("        <td>" + patientB.getMedicaid() + "</td>");
          out.println("        <td>" + patientB.getSsn() + "</td>");
          pos = patientB.getMrns().indexOf("-");
          out.println("        <td>" + (pos == -1 ? patientB.getMrns() : patientB.getMrns().substring(pos + 1))
              + "</td>");
          out.println("        <td>" + (pos == -1 ? "" : patientB.getMrns().substring(0, pos)) + "</td>");
          out.println("        <td>" + patientB.getAddressStreet1() + "</td>");
          out.println("        <td>" + patientB.getAddressCity() + "</td>");
          out.println("        <td>" + patientB.getAddressState() + "</td>");
          out.println("        <td>" + patientB.getAddressZip() + "</td>");
          out.println("        <td>" + patientB.getPhone() + "</td>");
          out.println("        <td>" + patientB.getMotherMaidenName() + "</td>");
          out.println("        <td>" + patientB.getMotherNameLast() + "</td>");
          out.println("        <td>" + patientB.getMotherNameFirst() + "</td>");
          out.println("        <td>" + patientB.getMotherNameMiddle() + "</td>");
          out.println("        <td>" + patientB.getVacName() + "</td>");
          out.println("        <td>" + patientB.getVacCode() + "</td>");
          out.println("        <td>" + patientB.getVacMfr() + "</td>");
          out.println("        <td>" + patientB.getVacDate() + "</td>");
          out.println("        <td></td>");
          out.println("      </tr>");
          out.flush();
        }
      }
      scriptOut.close();
      out.println("    </table>");
      out.println("    <h2>Specificity Cases</h2>");

      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr>");
      out.println("        <th>ID</th>");
      out.println("        <th>Expected Result</th>");
      out.println("        <th>Actual Result</th>");
      out.println("        <th>Pass/Fail</th>");
      out.println("        <th>Record Type</th>");
      out.println("        <th>Notes</th>");
      out.println("        <th>LastName</th>");
      out.println("        <th>FirstName</th>");
      out.println("        <th>MiddleName</th>");
      out.println("        <th>Suffix</th>");
      out.println("        <th>Sex</th>");
      out.println("        <th>DOB</th>");
      out.println("        <th>Medicaid ID</th>");
      out.println("        <th>SSN</th>");
      out.println("        <th>Medical Record ID</th>");
      out.println("        <th>Patient Org ID</th>");
      out.println("        <th>Street Address</th>");
      out.println("        <th>City</th>");
      out.println("        <th>State</th>");
      out.println("        <th>Zip</th>");
      out.println("        <th>Telephone</th>");
      out.println("        <th>MomMaiden</th>");
      out.println("        <th>MomLast</th>");
      out.println("        <th>MomFirst</th>");
      out.println("        <th>MomMiddle</th>");
      out.println("        <th>VacName</th>");
      out.println("        <th>VacCode</th>");
      out.println("        <th>VacMfr</th>");
      out.println("        <th>VacDate</th>");
      out.println("      </tr>");

      for (Transformer.SpecificityType specificityType : Transformer.SpecificityType.values()) {
        for (int i = 0; i < 6; i++) {
          Typest.Type typeSelected1 = Typest.Type.values()[random.nextInt(Typest.Type.values().length)];
          count++;

          Typest.Type typeA = typeSelected1;
          Transformer transformer = new Transformer();
          Patient patientA = null;
          Patient patientB = null;
          Patient patient = transformer.createPatient(Transformer.COMPLETE);
          Patient closeMatch = null;
          if (!patient.getBirthOrder().equals("") && !patient.getBirthOrder().equals("1")) {
            closeMatch = transformer.makeTwin(patient);
          } else {
            closeMatch = transformer.makeCloseMatch(patient);
          }
          patient.setGuardianNameFirst(patient.getMotherNameFirst());
          patient.setGuardianNameLast(patient.getMotherNameLast());
          closeMatch.setGuardianNameFirst(closeMatch.getMotherNameFirst());
          closeMatch.setGuardianNameLast(closeMatch.getMotherNameLast());
          Typest typest = new Typest(transformer);
          patientA = typest.type(patient, closeMatch, typeA, (Typest.Condition[]) null);
          patientB = transformer.makeCloseMatch(specificityType, patientA);
          transformer.changeMrn(patientB);
          String note = specificityType.getText() + " - " + typeSelected1;
          

          out.println("      <tr>");
          out.println("        <td>" + (count * 2 - 1) + "</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>Base</td>");
          out.println("        <td>" + note + "</td>");
          out.println("        <td>" + patientA.getNameLast() + "</td>");
          out.println("        <td>" + patientA.getNameFirst() + "</td>");
          out.println("        <td>" + patientA.getNameMiddle() + "</td>");
          out.println("        <td>" + patientA.getNameSuffix() + "</td>");
          out.println("        <td>" + patientA.getGender() + "</td>");
          out.println("        <td>" + patientA.getBirthDate() + "</td>");
          out.println("        <td>" + patientA.getMedicaid() + "</td>");
          out.println("        <td>" + patientA.getSsn() + "</td>");
          int pos = patientA.getMrns().indexOf("-");
          out.println("        <td>" + (pos == -1 ? patientA.getMrns() : patientA.getMrns().substring(pos + 1))
              + "</td>");
          out.println("        <td>" + (pos == -1 ? "" : patientA.getMrns().substring(0, pos)) + "</td>");
          out.println("        <td>" + patientA.getAddressStreet1() + "</td>");
          out.println("        <td>" + patientA.getAddressCity() + "</td>");
          out.println("        <td>" + patientA.getAddressState() + "</td>");
          out.println("        <td>" + patientA.getAddressZip() + "</td>");
          out.println("        <td>" + patientA.getPhone() + "</td>");
          out.println("        <td>" + patientA.getMotherMaidenName() + "</td>");
          out.println("        <td>" + patientA.getMotherNameLast() + "</td>");
          out.println("        <td>" + patientA.getMotherNameFirst() + "</td>");
          out.println("        <td>" + patientA.getMotherNameMiddle() + "</td>");
          out.println("        <td>" + patientA.getVacName() + "</td>");
          out.println("        <td>" + patientA.getVacCode() + "</td>");
          out.println("        <td>" + patientA.getVacMfr() + "</td>");
          out.println("        <td>" + patientA.getVacDate() + "</td>");
          out.println("        <td></td>");
          out.println("      </tr>");
          out.println("      <tr>");
          out.println("        <td>" + (count * 2) + "</td>");
          out.println("        <td>Non-Match</td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td>Test</td>");
          out.println("        <td>" + note + "</td>");
          out.println("        <td>" + patientB.getNameLast() + "</td>");
          out.println("        <td>" + patientB.getNameFirst() + "</td>");
          out.println("        <td>" + patientB.getNameMiddle() + "</td>");
          out.println("        <td>" + patientB.getNameSuffix() + "</td>");
          out.println("        <td>" + patientB.getGender() + "</td>");
          out.println("        <td>" + patientB.getBirthDate() + "</td>");
          out.println("        <td>" + patientB.getMedicaid() + "</td>");
          out.println("        <td>" + patientB.getSsn() + "</td>");
          pos = patientB.getMrns().indexOf("-");
          out.println("        <td>" + (pos == -1 ? patientB.getMrns() : patientB.getMrns().substring(pos + 1))
              + "</td>");
          out.println("        <td>" + (pos == -1 ? "" : patientB.getMrns().substring(0, pos)) + "</td>");
          out.println("        <td>" + patientB.getAddressStreet1() + "</td>");
          out.println("        <td>" + patientB.getAddressCity() + "</td>");
          out.println("        <td>" + patientB.getAddressState() + "</td>");
          out.println("        <td>" + patientB.getAddressZip() + "</td>");
          out.println("        <td>" + patientB.getPhone() + "</td>");
          out.println("        <td>" + patientB.getMotherMaidenName() + "</td>");
          out.println("        <td>" + patientB.getMotherNameLast() + "</td>");
          out.println("        <td>" + patientB.getMotherNameFirst() + "</td>");
          out.println("        <td>" + patientB.getMotherNameMiddle() + "</td>");
          out.println("        <td>" + patientB.getVacName() + "</td>");
          out.println("        <td>" + patientB.getVacCode() + "</td>");
          out.println("        <td>" + patientB.getVacMfr() + "</td>");
          out.println("        <td>" + patientB.getVacDate() + "</td>");
          out.println("        <td></td>");
          out.println("      </tr>");
        }
      }

      out.println("    </table>");

      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</Fpre>");
    }
    out.close();

  }

}
