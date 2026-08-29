package org.immregistries.mismo.trainer.random;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.immregistries.mismo.match.matchers.SimilarMatchNode;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.util.ReverseSignatureUtil;

/**
 * @author nathan
 */
public class Transformer
{

  private static final String LAST_NAME = "LAST_NAME";
  public static final String ETHNICITY = "ETHNICITY";
  public static final String RACE = "RACE";
  public static final String PHONE = "PHONE";
  public static final String ADDRESS = "ADDRESS";
  public static final String VAC2_HIST = "VAC2_HIST";
  public static final String VAC1_HIST = "VAC1_HIST";
  public static final String VAC3_HIST = "VAC3_HIST";
  public static final String VAC3_ADMIN = "VAC3_ADMIN";
  public static final String VAC2_ADMIN = "VAC2_ADMIN";
  public static final String TWIN_POSSIBLE = "TWIN_POSSIBLE";
  public static final String VAC1_ADMIN = "VAC1_ADMIN";
  public static final String DOB = "DOB";
  public static final String MOTHER = "MOTHER";
  public static final String FATHER = "FATHER";
  public static final String BOY_OR_GIRL = "BOY_OR_GIRL";
  public static final String GIRL = "GIRL";
  public static final String BOY = "BOY";
  public static final String MEDICAID = "MEDICAID";
  public static final String SSN = "SSN";
  public static final String MRN = "MRN";

  public static final String[] COMPLETE = { BOY_OR_GIRL, MOTHER, FATHER, DOB, ADDRESS, PHONE, RACE, ETHNICITY,
      MEDICAID, SSN, MRN, TWIN_POSSIBLE };

  private static Map<String, List<String[]>> conceptMap = null;
  private static Random random = new Random();

  /**
   * Finds a single random value from the concept map and returns it.
   * 
   * @param concept
   *          type of item looking for (e.g. BOY)
   * @return
   * @throws IOException
   */
  public String getValue(String concept) throws IOException {
    return getValue(concept, 0);
  }

  /**
   * Some concept types are multi-valued and this returns the exact part
   * desired. If there is no value then blank is returned.
   * 
   * @param concept
   *          type of item looking for (e.g. ADDRESS)
   * @param pos
   *          value from 0 on up
   * @return
   * @throws IOException
   */
  public String getValue(String concept, int pos) throws IOException {
    String value = "";
    if (conceptMap == null) {
      init();
    }
    List<String[]> valueList = conceptMap.get(concept);
    if (valueList != null) {
      String[] values = valueList.get(random.nextInt(valueList.size()));
      if (pos < values.length) {
        value = values[pos];
      }
    }
    return value;
  }

  /**
   * Returns attire array from randomly chosen concept. The array size indicated
   * is always returned even if there are not enough values to fill it. All
   * array values are non-null.
   * 
   * @param concept
   * @param size
   * @return
   * @throws IOException
   */
  public String[] getValueArray(String concept, int size) throws IOException {
    if (conceptMap == null) {
      init();
    }
    List<String[]> valueList = conceptMap.get(concept);
    String[] valueSourceList = null;
    if (valueList != null) {
      valueSourceList = valueList.get(random.nextInt(valueList.size()));
    }
    String[] values = new String[size];
    for (int i = 0; i < values.length; i++) {
      if (valueSourceList != null && i < valueSourceList.length) {
        values[i] = valueSourceList[i];
      } else {
        values[i] = "";
      }
    }
    return values;
  }

  /**
   * Creates random dates for patient age and vaccinations.
   * 
   * @param dates
   * @return
   */
  protected String createDates(String[] dates) {
    {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
      if (random.nextBoolean()) {
        // Setting up baby, 6 months old today
        // 6 month appointment
        Calendar cal6Month = Calendar.getInstance();
        dates[3] = sdf.format(cal6Month.getTime());

        // Born about 6 months before
        Calendar calBorn = Calendar.getInstance();
        calBorn.add(Calendar.MONTH, -6);
        calBorn.add(Calendar.DAY_OF_MONTH, 3 - random.nextInt(17));
        dates[0] = sdf.format(calBorn.getTime());

        // 4 month appointment
        Calendar cal4Month = Calendar.getInstance();
        cal4Month.setTime(calBorn.getTime());
        cal4Month.add(Calendar.MONTH, 4);
        cal4Month.add(Calendar.DAY_OF_MONTH, random.nextInt(12) - 3);
        dates[2] = sdf.format(cal4Month.getTime());

        // 2 month appointment
        Calendar cal2Month = Calendar.getInstance();
        cal2Month.setTime(calBorn.getTime());
        cal2Month.add(Calendar.MONTH, 2);
        cal4Month.add(Calendar.DAY_OF_MONTH, random.nextInt(10) - 3);
        dates[1] = sdf.format(cal2Month.getTime());

        return "BABY";
      } else {
        if (random.nextBoolean()) {
          // Setting up toddler
          Calendar calendar = Calendar.getInstance();
          // 4 years (today) - 48 months
          dates[3] = sdf.format(calendar.getTime());
          // 19 months
          calendar.add(Calendar.MONTH, 19 - 48);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[2] = sdf.format(calendar.getTime());
          // 12 months
          calendar.add(Calendar.MONTH, 12 - 19);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[1] = sdf.format(calendar.getTime());
          // birth
          calendar.add(Calendar.MONTH, -12);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[0] = sdf.format(calendar.getTime());
          return "TODDLER";
        } else {
          // Setting up toddler
          Calendar calendar = Calendar.getInstance();
          // 13 years (today)
          dates[3] = sdf.format(calendar.getTime());
          // 11 years
          calendar.add(Calendar.YEAR, -2);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[2] = sdf.format(calendar.getTime());
          // 9 years
          calendar.add(Calendar.YEAR, -2);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[1] = sdf.format(calendar.getTime());
          // birth
          calendar.add(Calendar.YEAR, -9);
          calendar.add(Calendar.DAY_OF_MONTH, 7 - random.nextInt(15));
          dates[0] = sdf.format(calendar.getTime());
          return "TWEEN";
        }
      }
    }
  }

  /**
   * Handles situation where value has a ~ in it, which indicates that the
   * replacement sometimes happens. The format of the transformation is like
   * this: field=~40%[BOY]:[GIRL] which would read the field varies and should
   * be 40% of the time [BOY] and otherwise [GIRL]. Method uses random method to
   * randomly pick with a rough distribution indicated.
   * 
   * @param t
   */
  protected void handleSometimes(Transform t) {
    int sometimes = 0;
    if (t.value.startsWith("~") && t.value.indexOf("%") != -1) {
      int perPos = t.value.indexOf("%");
      try {
        sometimes = Integer.parseInt(t.value.substring(1, perPos));
        t.value = t.value.substring(perPos + 1);
      } catch (NumberFormatException nfe) {
        // ignore
      }
    }
    if (sometimes > 0) {
      String part1 = t.value;
      String part2 = "";
      int colonPos = t.value.indexOf(":");
      if (colonPos != -1) {
        part1 = t.value.substring(0, colonPos);
        part2 = t.value.substring(colonPos + 1);
      }
      if (random.nextInt(100) >= sometimes) {
        t.value = part2;
        handleSometimes(t);
      } else {
        t.value = part1;
      }
    }
  }

  /**
   * Initializes the concept types that are in the transform.txt that has been
   * placed in this package.
   * 
   * @throws IOException
   */
  protected void init() throws IOException {
    conceptMap = new HashMap<String, List<String[]>>();
    BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/transform.txt")));
    String line;
    while ((line = in.readLine()) != null) {
      int equals = line.indexOf("=");
      if (equals != -1) {
        String concept = line.substring(0, equals);
        String[] values = line.substring(equals + 1).split("\\,");
        List<String[]> valueList = conceptMap.get(concept);
        if (valueList == null) {
          valueList = new ArrayList<String[]>();
          conceptMap.put(concept, valueList);
        }
        valueList.add(values);
      }
    }
  }

  /**
   * Creates a patient based on what is wanted in the patient object.
   * 
   * @param extras
   * @return
   * @throws IOException
   */
  public Patient createPatient(String[] extras) throws IOException {
    return createPatient(extras, "");
  }

  /**
   * Creates a patient based on the extras and quickTransforms.
   * 
   * @param extras
   * @param quickTransforms
   * @return
   * @throws IOException
   */
  public Patient createPatient(String[] extras, String quickTransforms) throws IOException {
    if (extras != null) {
      Set<String> extraSet = new HashSet<String>();
      for (String extra : extras) {
        extraSet.add(extra);
      }

      if (extraSet.contains(BOY)) {
        quickTransforms += Patient.NAME_LAST + "=~90%[LAST]:[MOTHER_MAIDEN]\n";
        quickTransforms += Patient.NAME_LAST_HYPH + "=[LAST_DIFFERENT]\n";
        quickTransforms += Patient.NAME_FIRST + "=[BOY]\n";
        quickTransforms += Patient.NAME_MIDDLE + "=~60%[BOY_MIDDLE]:~70%[BOY_MIDDLE_INITIAL]\n";
        quickTransforms += Patient.NAME_SUFFIX + "=~10%[SUFFIX]\n";
        quickTransforms += Patient.NAME_ALIAS + "=~5%[BOY_DIFFERENT]\n";
        if (extraSet.contains(GIRL)) {
          quickTransforms += Patient.GENDER + "=~50%M:F\n";
        } else {
          quickTransforms += Patient.GENDER + "=M\n";
        }
      }
      if (extraSet.contains(GIRL)) {
        quickTransforms += Patient.MOTHER_MAIDEN_NAME + "=~90%[LAST]:[MOTHER_MAIDEN]\n";
        quickTransforms += Patient.NAME_LAST_HYPH + "=[LAST_DIFFERENT]\n";
        quickTransforms += Patient.NAME_FIRST + "=[GIRL]\n";
        quickTransforms += Patient.NAME_MIDDLE + "=~60%[GIRL_MIDDLE]:~70%[GIRL_MIDDLE_INITIAL]\n";
        quickTransforms += Patient.GENDER + "=F\n";
        quickTransforms += Patient.NAME_ALIAS + "=~5%[GIRL_DIFFERENT]\n";
      }
      if (extraSet.contains(BOY_OR_GIRL)) {
        quickTransforms += Patient.MOTHER_MAIDEN_NAME + "=~90%[LAST]:[MOTHER_MAIDEN]\n";
        quickTransforms += Patient.NAME_FIRST + "=[BOY_OR_GIRL]\n";
        quickTransforms += Patient.NAME_LAST + "=~90%[LAST]:[MOTHER_MAIDEN]\n";
        quickTransforms += Patient.NAME_LAST_HYPH + "=[LAST_DIFFERENT]\n";
        quickTransforms += Patient.NAME_MIDDLE + "=~60%[BOY_OR_GIRL_MIDDLE]:~70%[GIRL_MIDDLE_INITIAL]\n";
        quickTransforms += Patient.GENDER + "=[GENDER]\n";
        quickTransforms += Patient.NAME_ALIAS + "=~5%[BOY_DIFFERENT]\n";
      }
      if (extraSet.contains(FATHER)) {
        quickTransforms += Patient.FATHER_NAME_LAST + "=~60%[LAST]:[LAST_DIFFERENT]\n";
        quickTransforms += Patient.FATHER_NAME_FIRST + "=[FATHER]\n";
      }
      if (extraSet.contains(MOTHER)) {
        quickTransforms += Patient.MOTHER_NAME_LAST + "=~60%[LAST]:[LAST_DIFFERENT]\n";
        quickTransforms += Patient.MOTHER_NAME_FIRST + "=[MOTHER]\n";
      }
      if (extraSet.contains(DOB)) {
        quickTransforms += Patient.BIRTH_DATE + "=[DOB]\n";
      }
      if (extraSet.contains(TWIN_POSSIBLE)) {
        quickTransforms += Patient.BIRTH_STATUS + "=[BIRTH_MULTIPLE]\n";
        quickTransforms += Patient.BIRTH_ORDER + "=[BIRTH_ORDER]\n";
        quickTransforms += Patient.BIRTH_TYPE + "=[BIRTH_TYPE]\n";
      }
      if (extraSet.contains(SSN)) {
        quickTransforms += Patient.SSN + "=[SSN]\n";
      }
      if (extraSet.contains(MRN)) {
        quickTransforms += Patient.MRNS + "=[MRN]\n";
      }
      if (extraSet.contains(MEDICAID)) {
        quickTransforms += Patient.MEDICAID + "=[MEDICAID]\n";
      }

      if (extraSet.contains(ADDRESS)) {
        quickTransforms += Patient.ADDRESS_STREET1 + "=[STREET]\n";
        quickTransforms += Patient.ADDRESS_STREET1_ALT + "=[STREETALT]\n";
        quickTransforms += Patient.ADDRESS_STREET2 + "=[STREET2]\n";
        quickTransforms += Patient.ADDRESS_CITY + "=[CITY]\n";
        quickTransforms += Patient.ADDRESS_STATE + "=[STATE]\n";
        quickTransforms += Patient.ADDRESS_ZIP + "=[ZIP]\n";
      }
      if (extraSet.contains(PHONE)) {
        quickTransforms += Patient.PHONE + "=[PHONE]\n";
      }
      if (extraSet.contains(RACE)) {
        quickTransforms += Patient.RACE + "=[RACE]\n";
      }
      if (extraSet.contains(ETHNICITY)) {
        quickTransforms += Patient.ETHNICITY + "=[ETHNICITY]\n";
      }
    }

    Patient patient = new Patient();
    transform(patient, quickTransforms);
    return patient;
  }

  private static String[][] streetNameReplacements = { { "Road", "Rd" }, { "Place", "Pl" }, { "Lane", "Ln" },
      { "Avenue", "Av" }, { "Park", "Pk" }, { "Square", "Sq" }, { "Drive", "Dr" }, { "Terrace", "Ter" },
      { "Circle", "Cir" }, { "Court", "Ct" }, { "Street", "St" }, { "Way", "wy" }, { "First", "1st" },
      { "Second", "2nd" }, { "Third", " 3rd" } };

  /**
   * Transform the basic patient and sets the values based on the block values
   * that the extra set.
   * 
   * @param patient
   * @param transformText
   * @throws IOException
   */
  protected void transform(Patient patient, String transformText) throws IOException {

    BufferedReader inTransform = new BufferedReader(new StringReader(transformText));
    String boyName = getValue(BOY);
    String girlName = getValue(GIRL);
    String differentBoyName = getValue(BOY);
    String differentGirlName = getValue(GIRL);
    String motherName = getValue(GIRL);
    String motherMaidenName = getValue(LAST_NAME);
    String fatherName = getValue(BOY);
    String lastName = getValue(LAST_NAME);
    String differentLastName = getValue(LAST_NAME);
    String middleNameBoy = getValue(BOY);
    String middleNameGirl = getValue(GIRL);
    String[] dates = new String[4];
    String vaccineType = createDates(dates);
    String gender = random.nextBoolean() ? "F" : "M";

    // Generate asian names
    if (random.nextDouble() > .85) {
      if (gender.equals("F")) { // Chinese girl
        girlName = getValue("CHINESE_GIRL") + getValue("CHINESE_GIRL").toLowerCase();

      } else {
        boyName = getValue("CHINESE_BOY") + getValue("CHINESE_BOY").toLowerCase();
      }

      lastName = getValue("CHINESE_LAST");
      middleNameBoy = " ";
      middleNameGirl = " ";
    }

    String[] vaccine1 = getValueArray("VACCINE_" + vaccineType, 5);
    String[] vaccine2 = getValueArray("VACCINE_" + vaccineType, 5);
    String[] vaccine3 = getValueArray("VACCINE_" + vaccineType, 5);
    String[] race = getValueArray(RACE, 2);
    String[] ethnicity = getValueArray(ETHNICITY, 2);
    String[] language = getValueArray("LANGUAGE", 2);
    String[] address = getValueArray(ADDRESS, 4);
    String[] vfc = getValueArray("VFC", 2);
    String suffix = getValue("SUFFIX");
    // String street = (random.nextInt(400) + 1) + " " +
    // getValue("STREET_NAME") + " " + getValue("STREET_ABBREVIATION");
    String street = (random.nextInt(400) + 1) + " " + getValue("STREET_NAME").trim();
    String streetalt = street;

    boolean found = false;
    for (int i = 0; i < 2; i++) {
      for (String[] replacement : streetNameReplacements) {
        int pos = streetalt.indexOf(" " + replacement[i]);
        if (pos != -1) {
          streetalt = streetalt.substring(0, pos) + " " + replacement[i == 0 ? 1 : 0]
              + streetalt.substring(pos + replacement[i].length() + 1);
          found = true;
          break;
        }
      }
      if (found) {
        break;
      }
    }

    if (!found) {
      streetalt = streetalt + " Street";
    }

    String[] apt = new String[] { "Suite ", "Apt ", "Apt #", "Unit " };
    String street2 = random.nextDouble() > .85 ? (apt[random.nextInt(4)]) + (random.nextInt(400) + 1) : "";
    String city = address[0];
    String state = address[1];
    String zip = address[2];
    String phoneArea = address[3];
    String phoneLocal = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + "-" + random.nextInt(10)
        + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
    String phone = "(" + phoneArea + ")" + phoneLocal;
    int birthCount = makeBirthCount();
    String ssn = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
        + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
    String medicaid = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
        + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);

    String mrn = "" + (random.nextInt(7) + 1) + "-" + (char) (random.nextInt(26) + 'A') + random.nextInt(10)
        + random.nextInt(10);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    String today = sdf.format(new Date());
    sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    String now = sdf.format(new Date());
    String line;
    while ((line = inTransform.readLine()) != null) {
      Transform t = new Transform();
      line = line.trim();
      if (line.length() > 0) {
        int posEqual = line.indexOf("=");
        if (posEqual != -1) {
          t.field = line.substring(0, posEqual).trim();
          t.value = line.substring(posEqual + 1);
          handleSometimes(t);
          if (t.value.equals("[BOY]")) {
            t.value = boyName;
          } else if (t.value.equals("[GIRL]")) {
            t.value = girlName;
          } else if (t.value.equals("[BOY_DIFFERENT]")) {
            t.value = differentBoyName;
          } else if (t.value.equals("[GIRL_DIFFERENT]")) {
            t.value = differentGirlName;
          } else if (t.value.equals("[BOY_OR_GIRL]")) {
            t.value = gender.equals("F") ? girlName : boyName;
          } else if (t.value.equals("[GENDER]")) {
            t.value = gender;
          } else if (t.value.equals("[FATHER]")) {
            t.value = fatherName;
          } else if (t.value.equals("[SUFFIX]")) {
            t.value = suffix;
          } else if (t.value.equals("[MOTHER]")) {
            t.value = motherName;
          } else if (t.value.equals("[MOTHER_MAIDEN]")) {
            t.value = motherMaidenName;
          } else if (t.value.equals("[DOB]")) {
            t.value = dates[0];
          } else if (t.value.equals("[BIRTH_MULTIPLE]")) {
            t.value = birthCount > 1 ? "Y" : "N";
          } else if (t.value.equals("[BIRTH_ORDER]")) {
            t.value = "" + birthCount;
          } else if (t.value.equals("[BIRTH_TYPE]")) {
            t.value = "" + birthCount;
          } else if (t.value.equals("[NOW]")) {
            t.value = now;
          } else if (t.value.equals("[RACE]")) {
            t.value = race[0];
          } else if (t.value.equals("[RACE_LABEL]")) {
            t.value = race[1];
          } else if (t.value.equals("[ETHNICITY]")) {
            t.value = ethnicity[0];
          } else if (t.value.equals("[ETHNICITY_LABEL]")) {
            t.value = ethnicity[1];
          } else if (t.value.equals("[LANGUAGE]")) {
            t.value = language[0];
          } else if (t.value.equals("[LANGUAGE_LABEL]")) {
            t.value = language[1];
          } else if (t.value.equals("[VFC]")) {
            t.value = vfc[0];
          } else if (t.value.equals("[SSN]")) {
            t.value = ssn;
          } else if (t.value.equals("[MRN]")) {
            t.value = mrn;
          } else if (t.value.equals("[MEDICAID]")) {
            t.value = medicaid;
          } else if (t.value.equals("[VFC_LABEL]")) {
            t.value = vfc[1];
          } else if (t.value.equals("[TODAY]")) {
            t.value = today;
          } else if (t.value.equals("[LAST]")) {
            t.value = lastName;
          } else if (t.value.equals("[LAST_DIFFERENT]")) {
            t.value = differentLastName;
          } else if (t.value.equals("[GIRL_MIDDLE]")) {
            t.value = middleNameGirl;
          } else if (t.value.equals("[BOY_MIDDLE]")) {
            t.value = middleNameBoy;
          } else if (t.value.equals("[BOY_OR_GIRL_MIDDLE]")) {
            t.value = gender.equals("F") ? middleNameGirl : middleNameBoy;
          } else if (t.value.equals("[GIRL_MIDDLE_INITIAL]")) {
            t.value = middleNameGirl.substring(0, 1);
          } else if (t.value.equals("[BOY_MIDDLE_INITIAL]")) {
            t.value = middleNameBoy.substring(0, 1);
          } else if (t.value.equals("[VAC1_DATE]")) {
            t.value = dates[1];
          } else if (t.value.equals("[VAC2_DATE]")) {
            t.value = dates[2];
          } else if (t.value.equals("[VAC3_DATE]")) {
            t.value = dates[3];
          } else if (t.value.equals("[VAC1_CVX]")) {
            t.value = vaccine1[0];
          } else if (t.value.equals("[VAC1_CVX_LABEL]")) {
            t.value = vaccine1[1];
          } else if (t.value.equals("[VAC1_LOT]")) {
            t.value = vaccine1[2];
          } else if (t.value.equals("[VAC1_MVX]")) {
            t.value = vaccine1[3];
          } else if (t.value.equals("[VAC1_MVX_LABEL]")) {
            t.value = vaccine1[4];
          } else if (t.value.equals("[VAC2_CVX]")) {
            t.value = vaccine2[0];
          } else if (t.value.equals("[VAC2_CVX_LABEL]")) {
            t.value = vaccine2[1];
          } else if (t.value.equals("[VAC2_LOT]")) {
            t.value = vaccine2[2];
          } else if (t.value.equals("[VAC2_MVX]")) {
            t.value = vaccine2[3];
          } else if (t.value.equals("[VAC2_MVX_LABEL]")) {
            t.value = vaccine2[4];
          } else if (t.value.equals("[VAC3_CVX]")) {
            t.value = vaccine3[0];
          } else if (t.value.equals("[VAC3_CVX_LABEL]")) {
            t.value = vaccine3[1];
          } else if (t.value.equals("[VAC3_LOT]")) {
            t.value = vaccine3[2];
          } else if (t.value.equals("[VAC3_MVX]")) {
            t.value = vaccine3[3];
          } else if (t.value.equals("[VAC3_MVX_LABEL]")) {
            t.value = vaccine3[4];
          } else if (t.value.equals("[CITY]")) {
            t.value = city;
          } else if (t.value.equals("[STREET]")) {
            t.value = street;
          } else if (t.value.equals("[STREETALT]")) {
            t.value = streetalt;
          } else if (t.value.equals("[STREET2]")) {
            t.value = street2;
          } else if (t.value.equals("[STATE]")) {
            t.value = state;
          } else if (t.value.equals("[ZIP]")) {
            t.value = zip;
          } else if (t.value.equals("[PHONE]")) {
            t.value = phone;
          } else if (t.value.equals("[PHONE_AREA]")) {
            t.value = phoneArea;
          } else if (t.value.equals("[PHONE_LOCAL]")) {
            t.value = phoneLocal;
          } else if (t.value.equals("[VAC3_DATE]")) {
            t.value = dates[3];
          }
          patient.setValue(t.field, t.value);
        }
      }
    }
  }

  public Patient makeCloseMatch(SpecificityType specificityType, Patient closeMatch) throws IOException {
    Patient patient = null;
    if (specificityType == SpecificityType.FIRST_NAME_SAME_DOB_DIFFERENT_MOM_DIFFERENT) {
      boolean same = true;
      while (same) {
        patient = createPatient(Transformer.COMPLETE);
        if (!patient.getBirthDate().equals(closeMatch.getBirthDate())) {
          patient.setMotherNameFirst(getValue(GIRL));
          patient.setMotherNameLast(getValue(LAST_NAME));
          if (!patient.getMotherNameFirst().equals(closeMatch.getMotherNameFirst())
              && !patient.getMotherNameLast().equals(closeMatch.getMotherNameLast())) {
            same = false;
          }
        }
      }
      patient.setNameFirst(closeMatch.getNameFirst());
    } else if (specificityType == SpecificityType.FIRST_NAME_SAME_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.FIRST_NAME_SAME_DOB_SAME_MOM_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(closeMatch.getBirthDate());
      patient.setMotherNameFirst(closeMatch.getMotherNameFirst());
      patient.setMotherNameLast(closeMatch.getMotherNameLast());
    } else if (specificityType == SpecificityType.LAST_NAME_DIFFERENT_FIRST_NAME_SAME_DOB_SAME) {
      boolean same = true;
      while (same) {
        patient = createPatient(Transformer.COMPLETE);
        if (!patient.getNameLast().equals(closeMatch.getNameLast())) {
          same = false;
        }
      }
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.LAST_NAME_DIFFERENT_FIRST_NAME_SAME_MIDDLE_NAME_SAME_DOB_SAME) {
      boolean same = true;
      while (same) {
        patient = createPatient(Transformer.COMPLETE);
        if (!patient.getNameLast().equals(closeMatch.getNameLast())) {
          same = false;
        }
      }
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setNameMiddle(closeMatch.getNameMiddle());
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_DOB_SIMILAR) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setBirthDate(changeDate(closeMatch.getBirthDate()));
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_MATCHES_MIDDLE_DOB_DIFFERENT) {
      boolean same = true;
      while (same) {
        patient = createPatient(Transformer.COMPLETE);
        if (!patient.getBirthDate().equals(closeMatch.getBirthDate())) {
          same = false;
        }
      }
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameFirst(closeMatch.getNameMiddle());
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SAME_DOB_DIFFERENT) {
      boolean same = true;
      while (same) {
        patient = createPatient(Transformer.COMPLETE);
        if (!patient.getBirthDate().equals(closeMatch.getBirthDate())) {
          same = false;
        }
      }
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameFirst(closeMatch.getNameFirst());
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SAME_DOB_SAME_SEX_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(closeMatch.getNameLast());
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SAME_DOB_SIMILAR) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameFirst(closeMatch.getNameFirst());
      patient.setBirthDate(changeDate(closeMatch.getBirthDate()));
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SIMILAR_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      if (patient.getGender().equals("M"))
      {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      }
      else
      {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setBirthDate(changeDate(closeMatch.getBirthDate()));
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SIMILAR_DOB_SIMILAR) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      if (patient.getGender().equals("M"))
      {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      }
      else
      {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setBirthDate(changeDate(closeMatch.getBirthDate()));
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAME_SIMILAR_SEX_DIFFERENT) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      if (patient.getGender().equals("M"))
      {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      }
      else
      {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setGender(closeMatch.getGender().equals("M") ? "F" : "M");
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_NAMES_ARE_TEMPORARY_BABY_NAMES_DOB_SAME_SEX_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setBirthDate(closeMatch.getBirthDate());
      patient.setGender(closeMatch.getGender());
      String name;
      if (random.nextBoolean())
      {
        name = "Newborn";
      }
      else
      {
        name = closeMatch.getGender().equals("M") ? "Baby Boy" : "Baby Girl";
      }
      patient.setNameFirst(name);
      closeMatch.setNameFirst(name);
      
    } else if (specificityType == SpecificityType.LAST_NAME_SAME_FIRST_SAME_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(closeMatch.getNameLast());
      patient.setBirthDate(closeMatch.getBirthDate());
      patient.setGender(closeMatch.getGender());
    } else if (specificityType == SpecificityType.LAST_NAME_SIMILAR_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(getValueClose(LAST_NAME, closeMatch.getNameLast(), 0, 0));
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.LAST_NAME_SIMILAR_FIRST_NAME_SIMILAR_DOB_SAME) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(getValueClose(LAST_NAME, closeMatch.getNameLast(), 0, 0));
      if (patient.getGender().equals("M"))
      {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      }
      else
      {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setBirthDate(closeMatch.getBirthDate());
    } else if (specificityType == SpecificityType.LAST_NAME_SIMILAR_FIRST_NAME_SIMILAR_DOB_SIMILAR) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setNameLast(getValueClose(LAST_NAME, closeMatch.getNameLast(), 0, 0));
      if (patient.getGender().equals("M"))
      {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      }
      else
      {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setBirthDate(changeDate(closeMatch.getBirthDate()));
    } else if (specificityType == SpecificityType.MOMS_ARE_SISTERS) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setMotherMaidenName(closeMatch.getMotherMaidenName());
    } else if (specificityType == SpecificityType.SIBLINGS_SAME_SEX) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setAddressCity(closeMatch.getAddressCity());
      patient.setAddressState(closeMatch.getAddressState());
      patient.setAddressStreet1(closeMatch.getAddressStreet1());
      patient.setAddressStreet2(closeMatch.getAddressStreet2());
      patient.setAddressZip(closeMatch.getAddressZip());
      patient.setEthnicity(closeMatch.getEthnicity());
      patient.setFatherNameFirst(closeMatch.getFatherNameFirst());
      patient.setFatherNameLast(closeMatch.getFatherNameLast());
      patient.setGender(closeMatch.getGender());
      patient.setGuardianNameFirst(closeMatch.getGuardianNameFirst());
      patient.setGuardianNameLast(closeMatch.getGuardianNameLast());
      patient.setMotherMaidenName(closeMatch.getMotherMaidenName());
      patient.setMotherNameFirst(closeMatch.getGuardianNameFirst());
      patient.setMotherNameLast(closeMatch.getGuardianNameLast());
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameLastHyph(closeMatch.getNameLastHyph());
      patient.setPhone(closeMatch.getPhone());
      patient.setRace(closeMatch.getRace());
    } else if (specificityType == SpecificityType.TWIN_DIFFERENT_SEX) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setAddressCity(closeMatch.getAddressCity());
      patient.setAddressState(closeMatch.getAddressState());
      patient.setAddressStreet1(closeMatch.getAddressStreet1());
      patient.setAddressStreet2(closeMatch.getAddressStreet2());
      patient.setAddressZip(closeMatch.getAddressZip());
      patient.setBirthDate(closeMatch.getBirthDate());
      patient.setEthnicity(closeMatch.getEthnicity());
      patient.setFatherNameFirst(closeMatch.getFatherNameFirst());
      patient.setFatherNameLast(closeMatch.getFatherNameLast());
      patient.setGender(closeMatch.getGender().equals("M") ? "F" : "M");
      patient.setGuardianNameFirst(closeMatch.getGuardianNameFirst());
      patient.setGuardianNameLast(closeMatch.getGuardianNameLast());
      patient.setMotherMaidenName(closeMatch.getMotherMaidenName());
      patient.setMotherNameFirst(closeMatch.getGuardianNameFirst());
      patient.setMotherNameLast(closeMatch.getGuardianNameLast());
      if (patient.getGender().equals("M")) {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      } else if (patient.getGender().equals("F")) {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameLastHyph(closeMatch.getNameLastHyph());
      patient.setPhone(closeMatch.getPhone());
      patient.setRace(closeMatch.getRace());
    } else if (specificityType == SpecificityType.TWIN_SAME_SEX) {
      patient = createPatient(Transformer.COMPLETE);
      patient.setAddressCity(closeMatch.getAddressCity());
      patient.setAddressState(closeMatch.getAddressState());
      patient.setAddressStreet1(closeMatch.getAddressStreet1());
      patient.setAddressStreet2(closeMatch.getAddressStreet2());
      patient.setAddressZip(closeMatch.getAddressZip());
      patient.setBirthDate(closeMatch.getBirthDate());
      patient.setEthnicity(closeMatch.getEthnicity());
      patient.setFatherNameFirst(closeMatch.getFatherNameFirst());
      patient.setFatherNameLast(closeMatch.getFatherNameLast());
      patient.setGender(closeMatch.getGender());
      patient.setGuardianNameFirst(closeMatch.getGuardianNameFirst());
      patient.setGuardianNameLast(closeMatch.getGuardianNameLast());
      patient.setMotherMaidenName(closeMatch.getMotherMaidenName());
      patient.setMotherNameFirst(closeMatch.getGuardianNameFirst());
      patient.setMotherNameLast(closeMatch.getGuardianNameLast());
      if (patient.getGender().equals("M")) {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0, 0));
      } else if (patient.getGender().equals("F")) {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0, 0));
      }
      patient.setNameLast(closeMatch.getNameLast());
      patient.setNameLastHyph(closeMatch.getNameLastHyph());
      patient.setPhone(closeMatch.getPhone());
      patient.setRace(closeMatch.getRace());
    } else {
      return makeCloseMatch(closeMatch);
    }
    return patient;
  }
  
  private String changeDate(String date)
  {
    if (date.length() < 8)
    {
      return "";
    }
    int i = random.nextInt(3);
    if (i == 0)
    {
      int v = Integer.parseInt(date.substring(3, 4)) - 1;
      if (v == -1)
      {
         v = 9;
      }
      return date.substring(0,3) + v + date.substring(4);
    }
    else if (i == 1)
    {
      int v = Integer.parseInt(date.substring(4, 6)) - 1;
      if (v == 0)
      {
         v = 2;
      }
      if (v < 10)
      {
        return date.substring(0,4)  + "0" + v + date.substring(6);
      }
      else
      {
        return date.substring(0,4) + v + date.substring(6);
      }
    }
    else if (i == 3)
    {
      int v = Integer.parseInt(date.substring(6, 8)) - 1;
      if (v == 0)
      {
         v = 2;
      }
      if (v < 10)
      {
        return date.substring(0,6)  + "0" + v;
      }
      else
      {
        return date.substring(0,6) + v;
      }
    }
    return date;
  }

  /**
   * Creates a patient that has similar attributes to the supplied patient. This
   * will allow for comparing to a record that is not a match but may have been
   * selected by a blocking set.
   * 
   * @param closeMatch
   * @return
   * @throws IOException
   */
  public Patient makeCloseMatch(Patient closeMatch) throws IOException {
    Patient patient = new Patient();
    patient.setGender(closeMatch.getGender());
    patient.setBirthDate(closeMatch.getBirthDate());
    if (has(closeMatch.getNameFirst())) {
      if (patient.getGender().equals("M")) {
        patient.setNameFirst(getValueClose(BOY, closeMatch.getNameFirst(), 0.10, 0.50));
      } else if (patient.getGender().equals("F")) {
        patient.setNameFirst(getValueClose(GIRL, closeMatch.getNameFirst(), 0.10, 0.50));
      }
    }
    if (has(closeMatch.getNameLast())) {
      patient.setNameLast(getValueClose(LAST_NAME, closeMatch.getNameLast(), 0.40, 0.50));
    }
    if (has(closeMatch.getNameAlias())) {
      addAlias(patient);
    }
    if (has(closeMatch.getMotherMaidenName())) {
      patient.setMotherMaidenName(getValueClose(GIRL, closeMatch.getMotherMaidenName(), 0.10, 0.50));
    }
    if (has(closeMatch.getMotherNameFirst())) {
      patient.setMotherNameFirst(getValueClose(GIRL, closeMatch.getMotherNameFirst(), 0.10, 0.50));
    }
    if (has(closeMatch.getMotherNameLast())) {
      patient.setMotherNameLast(getValueClose(LAST_NAME, closeMatch.getMotherNameLast(), 0.40, 0.50));
    }
    if (has(closeMatch.getFatherNameFirst())) {
      patient.setFatherNameFirst(getValueClose(BOY, closeMatch.getFatherNameFirst(), 0.40, 0.50));
    }
    if (has(closeMatch.getFatherNameLast())) {
      patient.setFatherNameLast(getValueClose(LAST_NAME, closeMatch.getFatherNameLast(), 0.40, 0.50));
    }
    if (has(closeMatch.getNameMiddle())) {
      if (patient.getGender().equals("M")) {
        patient.setNameMiddle(getValueClose(BOY, closeMatch.getNameMiddle(), 0.10, 0.50));
      } else if (patient.getGender().equals("F")) {
        patient.setNameMiddle(getValueClose(GIRL, closeMatch.getNameMiddle(), 0.10, 0.50));
      }
    }
    if (has(closeMatch.getEthnicity())) {
      patient.setEthnicity(getValue(ETHNICITY));
    }
    if (has(closeMatch.getRace())) {
      patient.setRace(getValue(RACE));
    }
    String[] address = getValueArray(ADDRESS, 4);
    if (has(closeMatch.getAddressStreet1())) {
      if (random.nextDouble() < 0.10) {
        patient.setAddressStreet1(closeMatch.getAddressStreet1());
        patient.setAddressCity(closeMatch.getAddressCity());
        patient.setAddressState(closeMatch.getAddressState());
        patient.setAddressZip(closeMatch.getAddressZip());
      } else {
        String street = (random.nextInt(400) + 1) + " " + getValue(LAST_NAME) + " " + getValue("STREET_ABBREVIATION");
        String city = address[0];
        String state = address[1];
        String zip = address[2];
        patient.setAddressStreet1(street);
        patient.setAddressCity(city);
        patient.setAddressState(state);
        patient.setAddressZip(zip);
      }
    }
    if (has(closeMatch.getPhone())) {
      String phoneArea = address[3];
      String phoneLocal = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + "-" + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);

      String phone = "(" + phoneArea + ")" + phoneLocal;
      if (random.nextDouble() < 0.10) {
        patient.setPhone(closeMatch.getPhone());
      } else {
        patient.setPhone(phone);
      }
    }
    if (has(closeMatch.getSsn())) {
      String ssn = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
      patient.setSsn(ssn);
    }
    if (has(closeMatch.getMedicaid())) {
      String medicaid = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
      patient.setMedicaid(medicaid);
    }
    if (has(closeMatch.getMrns())) {
      String mrn = "" + (random.nextInt(7) + 1) + "-" + (char) (random.nextInt(26) + 'A') + random.nextInt(10)
          + random.nextInt(10);
      patient.setMrns(mrn);
    }
    return patient;
  }


  /**
   * Creates a new record that appears to be a twin.
   * 
   * @param closeMatch
   * @return
   * @throws IOException
   */
  public Patient makeTwin(Patient closeMatch) throws IOException {
    Patient patient = new Patient();
    patient.setBirthOrder(closeMatch.getBirthOrder().equals("1") ? closeMatch.getBirthType() : "1");
    patient.setBirthStatus(closeMatch.getBirthStatus());
    patient.setBirthType(closeMatch.getBirthType());
    patient.setGender(closeMatch.getGender());
    patient.setBirthDate(closeMatch.getBirthDate());
    if (has(closeMatch.getNameFirst())) {
      // names should start with the same letter, this is often done in
      // twins
      String boyOrGirl = patient.getGender().equals("M") ? BOY : GIRL;
      patient.setNameFirst(getValueClose(boyOrGirl, closeMatch.getNameFirst(), 0, 0.40));
    }
    if (has(closeMatch.getNameLast())) {
      patient.setNameLast(closeMatch.getNameLast());
    }
    if (has(closeMatch.getNameAlias())) {
      addAlias(patient);
    }
    if (has(closeMatch.getMotherMaidenName())) {
      patient.setMotherMaidenName(closeMatch.getMotherMaidenName());
    }
    if (has(closeMatch.getMotherNameFirst())) {
      patient.setMotherNameFirst(closeMatch.getMotherNameFirst());
    }
    if (has(closeMatch.getMotherNameLast())) {
      patient.setMotherNameLast(closeMatch.getMotherNameLast());
    }
    if (has(closeMatch.getFatherNameFirst())) {
      patient.setFatherNameFirst(closeMatch.getFatherNameFirst());
    }
    if (has(closeMatch.getFatherNameLast())) {
      patient.setFatherNameLast(closeMatch.getFatherNameLast());
    }
    if (has(closeMatch.getNameMiddle())) {
      if (patient.getGender().equals("M")) {
        patient.setNameMiddle(getValueClose(BOY, closeMatch.getNameMiddle(), 0, 0.70));
      } else if (patient.getGender().equals("F")) {
        patient.setNameMiddle(getValueClose(GIRL, closeMatch.getNameMiddle(), 0, 0.70));
      }
    }
    if (has(closeMatch.getEthnicity())) {
      patient.setEthnicity(closeMatch.getEthnicity());
    }
    if (has(closeMatch.getRace())) {
      patient.setRace(closeMatch.getRace());
    }
    if (has(closeMatch.getAddressStreet1())) {
      patient.setAddressStreet1(closeMatch.getAddressStreet1());
      patient.setAddressCity(closeMatch.getAddressCity());
      patient.setAddressState(closeMatch.getAddressState());
      patient.setAddressZip(closeMatch.getAddressZip());
    }
    if (has(closeMatch.getPhone())) {
      patient.setPhone(closeMatch.getPhone());
    }
    if (has(closeMatch.getSsn())) {
      String ssn = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
      patient.setSsn(ssn);
    }
    if (has(closeMatch.getMedicaid())) {
      String medicaid = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
      patient.setMedicaid(medicaid);
    }
    if (has(closeMatch.getMrns())) {
      String mrn = "" + (random.nextInt(7) + 1) + "-" + (char) (random.nextInt(26) + 'A') + random.nextInt(10)
          + random.nextInt(10);
      patient.setMrns(mrn);
    }
    return patient;
  }

  /**
   * Returns a value that is the same, any other value, or similar to the value
   * being passed in.
   * 
   * @param field
   *          the name of the field to look at in the patient object
   * @param closeMatchValue
   *          the value to set close to
   * @param rateTheSame
   *          the rate at which to pick the closeMatchValue
   * @param rateAny
   *          the rate at which to pick any new value, after not picking
   *          closeMatchValue
   * @return
   * @throws IOException
   */
  private String getValueClose(String field, String closeMatchValue, double rateTheSame, double rateAny)
      throws IOException {
    if (random.nextDouble() < rateTheSame) {
      return closeMatchValue;
    }
    String value = getValue(field);
    if (random.nextDouble() < rateAny) {
      return value;
    }
    int count = 0;
    while (value.charAt(0) != closeMatchValue.charAt(0) && count < 1000) {
      value = getValue(field);
      count++;
    }
    return value;
  }

  /**
   * Create a realistic birth count based on real levels of multiple births
   * within the US population.
   * 
   * @return
   */
  protected int makeBirthCount() {
    int birthCount = 1;
    int hat = random.nextInt(100000);
    if (hat < 3220 + 149) {
      // chances for twin are 32.2 in 1,000 or 3220 in 100,000
      birthCount = 2;
      if (hat < 149) {
        // chances for triplet or higher is is 148.9 in 100,000
        birthCount = 3;
        if (hat < 10) {
          birthCount = 4;
          if (hat < 2) {
            birthCount = 5;
          }
        }
      }
    }
    return birthCount;
  }

  /**
   * Change the address of the patient to a new address, not used before.
   * 
   * @param patient
   * @throws IOException
   */
  public void changeAddress(Patient patient) throws IOException {
    String[] address = getValueArray(ADDRESS, 4);
    if (has(patient.getAddressStreet1())) {
      String street = (random.nextInt(400) + 1) + " " + getValue(LAST_NAME) + " " + getValue("STREET_ABBREVIATION");
      String city = address[0];
      String state = address[1];
      String zip = address[2];
      patient.setAddressStreet1(street);
      patient.setAddressCity(city);
      patient.setAddressState(state);
      patient.setAddressZip(zip);
    }
  }

  /**
   * Change the phone to a new phone that has not been used before.
   * 
   * @param patient
   * @throws IOException
   */
  public void changePhone(Patient patient) throws IOException {
    String[] address = getValueArray(ADDRESS, 4);
    if (has(patient.getPhone())) {
      String phoneArea = address[3];
      String phoneLocal = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + "-" + random.nextInt(10)
          + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
      String phone = "(" + phoneArea + ")" + phoneLocal;
      patient.setPhone(phone);
    }

  }

  /**
   * Generate a new mother maiden name.
   * 
   * @param patient
   * @throws IOException
   */
  public void changeMotherMaidenName(Patient patient) throws IOException {
    if (has(patient.getMotherMaidenName())) {
      patient.setMotherMaidenName(getValue(GIRL));
    }
  }

  /**
   * Add appropriate boy or girl alias to the data.
   * 
   * @param patient
   * @throws IOException
   */
  public void addAlias(Patient patient) throws IOException {
    if (patient.getGender().equals("F")) {
      patient.setNameAlias(getValue(GIRL));
    } else {
      patient.setNameAlias(getValue(BOY));
    }
  }

  /**
   * Add suffix.
   * 
   * @param patient
   * @throws IOException
   */
  public void addSuffix(Patient patient) throws IOException {
    if (patient.getGender().equals("F")) {
      patient.setNameAlias("II");
    } else {
      patient.setNameSuffix("Jr");
    }
  }

  /**
   * Change the first name to another gender specific name.
   * 
   * @param patient
   * @throws IOException
   */
  public void changeNameFirst(Patient patient) throws IOException {
    if (has(patient.getNameFirst())) {
      if (patient.getGender().equals("M")) {
        patient.setNameFirst(getValue(BOY));
      } else {
        patient.setNameFirst(getValue(GIRL));
      }

    }
  }

  /**
   * Change phone area code.
   * 
   * @param patient
   * @throws IOException
   */
  public void changePhoneAreaCode(Patient patient) throws IOException {
    if (has(patient.getPhone()) && patient.getPhone().length() == 13) {
      String phoneAreaOld = patient.getPhone().substring(1, 4);
      String phoneAreaNew = getValueArray(ADDRESS, 4)[3];
      while (phoneAreaOld.equals(phoneAreaNew)) {
        phoneAreaNew = getValueArray(ADDRESS, 4)[3];
      }
      String phoneLocal = patient.getPhone().substring(5);
      String phone = "(" + phoneAreaNew + ")" + phoneLocal;
      patient.setPhone(phone);
    }
  }

  /**
   * Change the MRN.
   * 
   * @param patient
   * @throws IOException
   */
  public void changeMrn(Patient patient) throws IOException {
    if (has(patient.getMrns())) {
      String mrn = "" + (random.nextInt(7) + 1) + "-" + (char) (random.nextInt(26) + 'A') + random.nextInt(10)
          + random.nextInt(10);
      patient.setMrns(mrn);
    }
  }

  public boolean has(String s) {
    return s != null && !s.equals("");
  }

  public static enum SpecificityType {
    FIRST_NAME_SAME_DOB_DIFFERENT_MOM_DIFFERENT("First name same, DOB different, mom different"), FIRST_NAME_SAME_DOB_SAME(
        "First name same, DOB same"), FIRST_NAME_SAME_DOB_SAME_MOM_SAME("First name same, DOB same, mom same"), LAST_NAME_DIFFERENT_FIRST_NAME_SAME_DOB_SAME(
        "Last name different, first name same, dob same"), LAST_NAME_DIFFERENT_FIRST_NAME_SAME_MIDDLE_NAME_SAME_DOB_SAME(
        "Last name different, first name same, middle name same, dob same"),  LAST_NAME_SAME_DOB_SAME("Last name same, dob same"), LAST_NAME_SAME_DOB_SIMILAR(
        "Last name same, dob similar"), LAST_NAME_SAME_FIRST_MATCHES_MIDDLE_DOB_DIFFERENT(
        "Last name same, first matches middle, DOB different"), LAST_NAME_SAME_FIRST_NAME_SAME_DOB_DIFFERENT(
        "Last name same, first name same, DOB different"), LAST_NAME_SAME_FIRST_NAME_SAME_DOB_SAME_SEX_SAME(
        "Last name same, first name same, dob same, sex same"), LAST_NAME_SAME_FIRST_NAME_SAME_DOB_SIMILAR(
        "Last name same, first name same, DOB similar"), LAST_NAME_SAME_FIRST_NAME_SIMILAR_DOB_SAME(
        "Last name same, first name similar, dob same"), LAST_NAME_SAME_FIRST_NAME_SIMILAR_DOB_SIMILAR(
        "Last name same, first name similar, dob similar"), LAST_NAME_SAME_FIRST_NAME_SIMILAR_SEX_DIFFERENT(
        "Last name same, first name similar, sex different"), LAST_NAME_SAME_FIRST_NAMES_ARE_TEMPORARY_BABY_NAMES_DOB_SAME_SEX_SAME(
        "Last name same, first names are temporary baby names, DOB same, sex same"), LAST_NAME_SAME_FIRST_SAME_DOB_SAME(
        "Last name same, first same, dob same"), LAST_NAME_SIMILAR_DOB_SAME(
        "Last name similar, dob same"), LAST_NAME_SIMILAR_FIRST_NAME_SIMILAR_DOB_SAME(
        "Last name similar, first name similar, dob same"), LAST_NAME_SIMILAR_FIRST_NAME_SIMILAR_DOB_SIMILAR(
        "Last name similar, first name similar, dob similar"), MOMS_ARE_SISTERS("Moms are sisters"), SIBLINGS_SAME_SEX(
        "Siblings same sex"), TWIN_DIFFERENT_SEX("Twin different sex"), TWIN_SAME_SEX(
        "Twin same sex"),

    ;
    private String text = "";

    public String getText() {
      return text;
    }

    private SpecificityType(String text) {
      this.text = text;
    }
  }
  public static void main(String[] args) throws Exception {
    String exampleSig = "CighvC0g1qGhA2U:hKBAQA:hKBAQA:hKBAQA:hKBAQA";
    //String exampleSig = "CighvC0g1qGhA2U:Qoziao:AI7ye4:QoTi6o:AITiao";
    if(args.length == 1){
        exampleSig = args[0];
    }
    System.out.println("STARTING");
    List<String> stuff = ReverseSignatureUtil.reverseSignatureIntoHexScores(exampleSig);
    System.out.println(stuff);

    Transformer t = new Transformer();
    t.init();
    double d = -1.0;
    HashMap<String, String> resultMap = new HashMap<>();
    Patient patA = new Patient();
    Patient patB = new Patient();
    if(stuff.get(0).equals("f")){
        String value1 = t.getValue("LAST_NAME");
        patA.setNameLast(value1);
        patB.setNameLast(value1);
    } else{
        for(int i=0; i<10000; i++){
            String value1 = t.getValue("LAST_NAME");
            String value2 = t.getValue("LAST_NAME");
            patA.setNameLast(value1);
            patB.setNameLast(value2);
            SimilarMatchNode smn = new SimilarMatchNode();
            smn.setFieldName("nameLast");
            d = smn.score(patA, patB);
            int i1 = (int) (d * 15.0);
            String hexScore = Integer.toHexString(i1);
            //L-simiar is [1]
            if(hexScore.equals(stuff.get(1))){
                System.out.println("LAST NAME");
                System.out.println(hexScore);
                break;
            }
        } 
    }
    if(stuff.get(5).equals("f")){
        String value1 = t.getValue("GIRL");
        patA.setNameFirst(value1);
        patB.setNameFirst(value1);
    } else{
        for(int i=0; i<10000; i++){
            String value1 = t.getValue("GIRL");
            String value2 = t.getValue("GIRL");
            patA.setNameFirst(value1);
            patB.setNameFirst(value2);
            SimilarMatchNode smn = new SimilarMatchNode();
            smn.setFieldName("nameFirst");
            d = smn.score(patA, patB);
            int i1 = (int) (d * 15.0);
            String hexScore = Integer.toHexString(i1);
            //F-similar is [6]
            if(hexScore.equals(stuff.get(6))){
                System.out.println("FIRST NAME");
                System.out.println(hexScore);
                break;
            }
        }
    }
    if(stuff.get(10).equals("f")){
        String value1 = t.getValue("GIRL");
        patA.setNameMiddle(value1);
        patB.setNameMiddle(value1);
    } else{
        for(int i=0; i<10000; i++){
            String value1 = t.getValue("GIRL");
            String value2 = t.getValue("GIRL");
            patA.setNameMiddle(value1);
            patB.setNameMiddle(value2);
            SimilarMatchNode smn = new SimilarMatchNode();
            smn.setFieldName("nameMiddle");
            d = smn.score(patA, patB);
            int i1 = (int) (d * 15.0);
            String hexScore = Integer.toHexString(i1);
            //M-similar is [12]
            if(hexScore.equals(stuff.get(12))){
                System.out.println("MIDDLE NAME");
                System.out.println(hexScore);
                break;
            }
        }
    }
    String phoneArea = t.getValue("ADDRESS", 3);
    String phoneLocal = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + "-" + random.nextInt(10)
        + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
    String phone = "(" + phoneArea + ")" + phoneLocal;
    patA.setPhone(phone);
    patB.setPhone(phone);
    if(stuff.get(3).equals("0")){
        phoneArea = t.getValue("ADDRESS", 3);
        phoneLocal = "" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10) + "-" + random.nextInt(10)
            + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
        phone = "(" + phoneArea + ")" + phoneLocal;
        patB.setPhone(phone);
    }

    String street = (random.nextInt(400) + 1) + " " + t.getValue(LAST_NAME) + " " + t.getValue("STREET_ABBREVIATION");
    patA.setAddressStreet1(street);
    patB.setAddressStreet1(street);
    String addressState = t.getValue("ADDRESS", 1);
    patA.setAddressState(addressState);
    patB.setAddressState(addressState);
    String addressZip = t.getValue("ADDRESS", 2);
    patA.setAddressZip(addressZip);
    patB.setAddressZip(addressZip);
    if(stuff.get(4).equals("0")){
        street = (random.nextInt(400) + 1) + " " + t.getValue(LAST_NAME) + " " + t.getValue("STREET_ABBREVIATION");
        addressState = t.getValue("ADDRESS", 1);
        addressZip = t.getValue("ADDRESS", 2);
        patB.setAddressStreet1(street);
        patB.setAddressState(addressState);
        patB.setAddressZip(addressZip);
    }

    long minDay = LocalDate.of(1970, 1, 1).toEpochDay();
    long maxDay = LocalDate.of(2015, 12, 31).toEpochDay();
    long randomDay = random.nextLong(minDay, maxDay);
    LocalDate randomDate = LocalDate.ofEpochDay(randomDay);
    String birthDate = randomDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    patA.setBirthDate(birthDate);
    patB.setBirthDate(birthDate);
    if(stuff.get(8).equals("0")){
        randomDay = random.nextLong(minDay, maxDay);
        randomDate = LocalDate.ofEpochDay(randomDay);
        birthDate = randomDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        patB.setBirthDate(birthDate);
    }
    

    System.out.println(patA);
    System.out.println(patB);
  }
}
