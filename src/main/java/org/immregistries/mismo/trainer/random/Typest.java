package org.immregistries.mismo.trainer.random;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.immregistries.mismo.match.model.Patient;

/**
 * The typest represents a person or entity that enters or moves patient data.
 * This typest has the potential of making all the different kind of mistakes
 * that a real typest or user could make and only has access to a varying amount
 * of base data.
 * 
 * @author Nathan Bunker
 * 
 */
public class Typest {

	public enum Type {
		IDEAL, GREATA, GOODA, GOODB, POOR, BAD
	};

	public enum ChallengeCategory {
		RECORDING_AND_TYPOS, MISSING_DATA, VALUE_CHANGE, COMMON_VALUES, SPECIAL_CHARS, EXTRA_DATA, NON_STANDARD_DATA, TIMELINESS
	}

	public enum Condition {
		ADDRESS_CHANGED(ChallengeCategory.VALUE_CHANGE, "Patient Address"), 
		ADDRESS_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Address"), 
		ADDRESS_STREET_SUBSTITUTION(ChallengeCategory.COMMON_VALUES, "Patient Address Street"), 
		ADDRESS_STREET_MISSING(ChallengeCategory.MISSING_DATA, "Patient Address Street"), 
//		ALIAS_MISSING(ChallengeCategory.MISSING_DATA, "Patient Alias"),
//		BIRTH_MULTIPLE_MISSING(ChallengeCategory.MISSING_DATA, "Patient Multiple Birth Designation"),
//		BIRTH_MULITPLE_MISSING_FOR_TWIN(ChallengeCategory.MISSING_DATA, "Patient Multiple Birth Designation"),
//		BIRTH_ORDER_MISSING(ChallengeCategory.MISSING_DATA, "Patient Birth Order"),
//		BIRTH_ORDER_MISSING_FOR_TWIN(ChallengeCategory.MISSING_DATA, "Patient Birder Order"),
		DOB_VALUE_SWAPPED(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Date of Birth"), 
		DOB_OFF_BY_1(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Date of Birth"), 
		FIRST_NAME_CHANGED(ChallengeCategory.VALUE_CHANGE, "Patient First Name"), 
		FIRST_NAME_MATCHES_MIDDLE(ChallengeCategory.NON_STANDARD_DATA, "Patient First and Middle Name"), 
		FIRST_NAME_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient First Name"), 
		FIRST_NAME_TYPO_EXTRANEOUS_DATA(ChallengeCategory.EXTRA_DATA, "Patient First Name"), 
		FIRST_NAME_TYPO_SPECIAL_CHARACTERS(ChallengeCategory.RECORDING_AND_TYPOS, "Patient First Name"), 
		FIRST_NAME_TYPO_WRONG_VALUE(ChallengeCategory.RECORDING_AND_TYPOS, "Patient First Name"), 
//		GUARDIAN_FIRST_MISSING(ChallengeCategory.MISSING_DATA, "Guardian First Name"),
//		GUARDIAN_LAST_MISSING(ChallengeCategory.MISSING_DATA, "Guardian Last Name"),
		LAST_NAME_HYPHENATED(ChallengeCategory.SPECIAL_CHARS, "Patient Last Name"), 
		LAST_NAME_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Last Name"), 
//		MEDICAID_NUM_MISSING(ChallengeCategory.MISSING_DATA, "Patient Medicaid Number"),
//		MEDICAID_NUM_SHARED(ChallengeCategory.VALUE_CHANGE, "Patient Medicaid Number"),
//		MEDICAID_NUM_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Medicaid Number"),
		MIDDLE_NAME_INITIAL(ChallengeCategory.MISSING_DATA, "Patient Middle Name"), 
		MIDDLE_NAME_MISSING(ChallengeCategory.MISSING_DATA, "Patient Middle Name"), 
		MIDDLE_NAME_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient Middle Name"), 
//		MOTHERS_MAIDEN_NAME_CHANGED(ChallengeCategory.VALUE_CHANGE, "Mothers Maiden Name"),
//		MOTHERS_MAIDEN_NAME_MISSING(ChallengeCategory.MISSING_DATA, "Mothers Maiden Name"),
//		MOTHERS_MAIDEN_NAME_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Mothers Maiden Name"),
//		MRN_NOT_DEDUPLICATED(ChallengeCategory.VALUE_CHANGE, "Patient MRN"),
//		MRN_SHARED_MRN(ChallengeCategory.VALUE_CHANGE, "Patient MRN"),
		PHONE_AREA_CODE_CHANGE(ChallengeCategory.VALUE_CHANGE, "Patient Phone"), 
		PHONE_CHANGED(ChallengeCategory.VALUE_CHANGE, "Patient Phone");
//		SHOT_HISTORY_INCOMPLETE(ChallengeCategory.MISSING_DATA, "Shot History"),
//		SHOT_HISTORY_MISSING(ChallengeCategory.MISSING_DATA, "Shot History"),
//		SSN_MISSING(ChallengeCategory.MISSING_DATA, "Patient SSN"),
//		SSN_SHARED(ChallengeCategory.VALUE_CHANGE, "Patient SSN"),
//		SSN_TYPO(ChallengeCategory.RECORDING_AND_TYPOS, "Patient SSN"),
//		SUFFIX_MISSING(ChallengeCategory.MISSING_DATA, "Patient Suffix");

		private ChallengeCategory type;
		public ChallengeCategory getType() {
			return type;
		}

		public void setType(ChallengeCategory type) {
			this.type = type;
		}

		private String field;

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}

		Condition(ChallengeCategory type, String field) {
			this.type = type;
			this.field = field;
		}
	}

	private Transformer transformer = null;
	private Random random = new Random();

	public Typest(Transformer transformer) {
		this.transformer = transformer;
	}

	private static boolean everyOther = false;

	public Patient type(Patient copy, Patient closeMatch, Type type,
			Condition[] conditions) throws IOException {
		Patient patient = new Patient();
		if (type == Type.IDEAL) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameAlias(copy.getNameAlias());
			patient.setNameMiddle(copy.getNameMiddle());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setNameSuffix(copy.getNameSuffix());
			patient.setBirthDate(copy.getBirthDate());
			patient.setGuardianNameFirst(copy.getGuardianNameFirst());
			patient.setGuardianNameLast(copy.getGuardianNameLast());
			patient.setMotherMaidenName(copy.getMotherMaidenName());
			patient.setPhone(copy.getPhone());
			patient.setAddressStreet1(copy.getAddressStreet1());
			patient.setAddressStreet1Alt(copy.getAddressStreet1Alt());
			patient.setAddressStreet2(copy.getAddressStreet2());
			patient.setAddressCity(copy.getAddressCity());
			patient.setAddressState(copy.getAddressState());
			patient.setAddressZip(copy.getAddressZip());
			patient.setGender(copy.getGender());
			patient.setBirthStatus(copy.getBirthStatus());
			patient.setBirthType(copy.getBirthType());
			patient.setBirthOrder(copy.getBirthOrder());
			patient.setShotHistory(copy.getShotHistory());
			patient.setMrns(copy.getMrns());
			patient.setSsn(copy.getSsn());
			patient.setMedicaid(copy.getMedicaid());
		} else if (type == Type.GREATA) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameAlias(copy.getNameAlias());
			patient.setNameMiddle(copy.getNameMiddle());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setNameSuffix(copy.getNameSuffix());
			patient.setBirthDate(copy.getBirthDate());
			patient.setGuardianNameFirst(copy.getGuardianNameFirst());
			patient.setGuardianNameLast(copy.getGuardianNameLast());
			patient.setPhone(copy.getPhone());
			patient.setAddressStreet1(copy.getAddressStreet1());
			patient.setAddressStreet1Alt(copy.getAddressStreet1Alt());
			patient.setAddressCity(copy.getAddressCity());
			patient.setAddressState(copy.getAddressState());
			patient.setAddressZip(copy.getAddressZip());
			patient.setGender(copy.getGender());
			patient.setShotHistory(copy.getShotHistory());
			patient.setMrns(copy.getMrns());
			patient.setSsn(copy.getSsn());
		} else if (type == Type.GOODA) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameMiddle(copy.getNameMiddle());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setBirthDate(copy.getBirthDate());
			patient.setGuardianNameFirst(copy.getGuardianNameFirst());
			patient.setGuardianNameLast(copy.getGuardianNameLast());
			patient.setAddressStreet1(copy.getAddressStreet1());
			patient.setAddressStreet1Alt(copy.getAddressStreet1Alt());
			patient.setAddressCity(copy.getAddressCity());
			patient.setAddressState(copy.getAddressState());
			patient.setAddressZip(copy.getAddressZip());
			patient.setGender(copy.getGender());
			patient.setShotHistory(copy.getShotHistory());
			patient.setMrns(copy.getMrns());
		} else if (type == Type.GOODB) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameMiddle(copy.getNameMiddle());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setBirthDate(copy.getBirthDate());
			patient.setGuardianNameFirst(copy.getGuardianNameFirst());
			patient.setMotherMaidenName(copy.getMotherMaidenName());
			patient.setAddressStreet1(copy.getAddressStreet1());
			patient.setAddressStreet1Alt(copy.getAddressStreet1Alt());
			patient.setAddressCity(copy.getAddressCity());
			patient.setAddressState(copy.getAddressState());
			patient.setAddressZip(copy.getAddressZip());
			patient.setGender(copy.getGender());
			patient.setShotHistory(copy.getShotHistory());
			patient.setMrns(copy.getMrns());
		} else if (type == Type.POOR) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setBirthDate(copy.getBirthDate());
			patient.setAddressStreet1(copy.getAddressStreet1());
			patient.setAddressStreet1Alt(copy.getAddressStreet1Alt());
			patient.setAddressCity(copy.getAddressCity());
			patient.setAddressState(copy.getAddressState());
			patient.setAddressZip(copy.getAddressZip());
			patient.setGender(copy.getGender());
			patient.setShotHistory(copy.getShotHistory());
			patient.setMrns(copy.getMrns());
		} else if (type == Type.BAD) {
			patient.setNameFirst(copy.getNameFirst());
			patient.setNameLast(copy.getNameLast());
			patient.setNameLastHyph(copy.getNameLastHyph());
			patient.setBirthDate(copy.getBirthDate());
			patient.setGender(copy.getGender());
			patient.setMrns(copy.getMrns());
		}

		if (conditions == null) {
			return patient;
		}
		for (Condition condition : conditions) {
		if (condition == Condition.ADDRESS_CHANGED) {
			transformer.changeAddress(patient);
		} else if (condition == Condition.ADDRESS_TYPO) {
			patient.setAddressCity(makeTypo(patient.getAddressCity()));
			patient.setAddressStreet1(makeTypo(patient.getAddressStreet1()));
			patient.setAddressStreet2(makeTypo(patient.getAddressStreet2()));
			patient.setAddressZip(makeTypo(patient.getAddressZip()));

		} else if (condition == Condition.ADDRESS_STREET_MISSING) {
			patient.setAddressCity("");
			patient.setAddressState("");
			patient.setAddressStreet1("");
			patient.setAddressStreet2("");
			patient.setAddressZip("");
		} else if (condition == Condition.ADDRESS_STREET_SUBSTITUTION) {
			patient.setAddressStreet1(patient.getAddressStreet1Alt());
		} else if (condition == Condition.DOB_VALUE_SWAPPED) {
			if (!patient.getBirthDate().equals("")) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				try {
					Date birthDate = sdf.parse(patient.getBirthDate());
					Calendar cal = Calendar.getInstance();
					cal.setTime(birthDate);
					int month = cal.get(Calendar.MONTH) + 1;
					int day = cal.get(Calendar.DAY_OF_MONTH);
					if (day > 12) {
						day = month;
					}
					cal.set(Calendar.MONTH, day - 1);
					cal.set(Calendar.DAY_OF_MONTH, month);
					patient.setBirthDate(sdf.format(cal.getTime()));
				} catch (ParseException nfe) {
					throw new IOException("Unable to parse date of birth", nfe);
				}
			}

		} else if (condition == Condition.DOB_OFF_BY_1) {
			if (!patient.getBirthDate().equals("")) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				try {
					Date birthDate = sdf.parse(patient.getBirthDate());
					Calendar cal = Calendar.getInstance();
					cal.setTime(birthDate);
					cal.add(Calendar.DAY_OF_MONTH, (random.nextBoolean() ? 1
							: -1));
					patient.setBirthDate(sdf.format(cal.getTime()));
				} catch (ParseException nfe) {
					throw new IOException("Unable to parse date of birth", nfe);
				}
			}
		} else if (condition == Condition.FIRST_NAME_CHANGED) {
			transformer.changeNameFirst(patient);
		} else if (condition == Condition.FIRST_NAME_MATCHES_MIDDLE) {
			String nameFirst = patient.getNameFirst();
			String nameMiddle = patient.getNameMiddle();
			patient.setNameFirst(nameMiddle);
			patient.setNameMiddle(nameFirst);
		} else if (condition == Condition.FIRST_NAME_TYPO) {
			patient.setNameFirst(makeTypo(patient.getNameFirst()));
		} else if (condition == Condition.FIRST_NAME_TYPO_EXTRANEOUS_DATA) {
			if (random.nextBoolean()) {
				patient.setNameFirst("**MOGE** " + patient.getNameFirst());

			} else if (random.nextBoolean()) {
				patient.setNameFirst("ZZZ" + patient.getNameFirst());
			} else {
				patient.setNameFirst("DELETE " + patient.getNameFirst());
			}
		} else if (condition == Condition.FIRST_NAME_TYPO_SPECIAL_CHARACTERS) {
			patient.setNameFirst(makeTypo(patient.getNameFirst()) + "\\");
		} else if (condition == Condition.FIRST_NAME_TYPO_WRONG_VALUE) {
			if (random.nextBoolean()) {
				patient.setNameFirst(patient.getAddressStreet1());

			} else if (random.nextBoolean()) {
				patient.setNameFirst(patient.getAddressCity());

			} else if (random.nextBoolean()) {
				patient.setNameFirst(patient.getSsn());
			} else {
				patient.setNameFirst(patient.getGuardianNameFirst());
			}
		} else if (condition == Condition.LAST_NAME_TYPO) {
			patient.setNameLast(makeTypo(patient.getNameLast()));
		} else if (condition == Condition.LAST_NAME_HYPHENATED) {
			if (patient.getNameLast().equals(patient.getNameLastHyph())) {
				patient.setNameLast(patient.getNameLast() + "-"
						+ patient.getMotherMaidenName());
			} else {
				if (everyOther) {
					patient.setNameLast(patient.getNameLast() + "-"
							+ patient.getNameLastHyph());
				} else {
					patient.setNameLast(patient.getNameLastHyph() + "-"
							+ patient.getNameLast());
				}
				everyOther = !everyOther;
			}
		} else if (condition == Condition.MIDDLE_NAME_INITIAL) {
			String middleName = patient.getNameMiddle();
			if (middleName.length() > 1) {
				middleName = middleName.substring(0, 1);
			}
			patient.setNameMiddle(middleName);
		} else if (condition == Condition.MIDDLE_NAME_MISSING) {
			patient.setNameMiddle("");
		} else if (condition == Condition.MIDDLE_NAME_TYPO) {
			patient.setNameMiddle(makeTypo(patient.getNameMiddle()));
		} else if (condition == Condition.PHONE_AREA_CODE_CHANGE) {
			transformer.changePhoneAreaCode(patient);
		} else if (condition == Condition.PHONE_CHANGED) {
			transformer.changePhone(patient);

		}
		}
		return patient;
	}

	
	/**
	 * This method takes a patient and a close match and creates a new patient
	 * so that it has the conditions indicated in the parameters.
	 * 
	 * @param copy
	 *            the patient object that needs to be typed
	 * @param closeMatch
	 *            the closely matched patient this will be compared to
	 * @param type
	 *            the amount of information that may be copied over
	 * @param condition
	 *            an error condition that the typest is supposed to replicate
	 * @return a new patient object with the issues and problems indicated
	 * @throws IOException
	 */
	public Patient type(Patient copy, Patient closeMatch, Type type,
			Condition condition) throws IOException {
		return type(copy, closeMatch, type, new Condition[] {condition});
	}

	// This is a table of common letters that are near other ones on the
	// keyboard.
	// This allows the typest to make bad key entries as if the finger was
	// momentarily
	// on the wrong key.
	private static final Map<Character, char[]> NEARBY = new HashMap<Character, char[]>();
	static {
		NEARBY.put('1', new char[] { '2', '`' });
		NEARBY.put('2', new char[] { '3', '1' });
		NEARBY.put('3', new char[] { '4', '2' });
		NEARBY.put('4', new char[] { '5', '3' });
		NEARBY.put('5', new char[] { '6', '4' });
		NEARBY.put('6', new char[] { '7', '5' });
		NEARBY.put('7', new char[] { '8', '6' });
		NEARBY.put('8', new char[] { '9', '7' });
		NEARBY.put('9', new char[] { '0', '8' });
		NEARBY.put('0', new char[] { '-', '9' });
		NEARBY.put('q', new char[] { 'w' });
		NEARBY.put('w', new char[] { 'q', 'e' });
		NEARBY.put('e', new char[] { 'w', 'r' });
		NEARBY.put('r', new char[] { 'e', 't' });
		NEARBY.put('t', new char[] { 'r', 'u' });
		NEARBY.put('y', new char[] { 't', 'u' });
		NEARBY.put('u', new char[] { 'y', 'i' });
		NEARBY.put('i', new char[] { 'u', 'o' });
		NEARBY.put('o', new char[] { 'i', 'p' });
		NEARBY.put('p', new char[] { 'o', '[' });
		NEARBY.put('a', new char[] { 's' });
		NEARBY.put('s', new char[] { 'd', 'a' });
		NEARBY.put('d', new char[] { 'f', 's' });
		NEARBY.put('f', new char[] { 'g', 'd' });
		NEARBY.put('g', new char[] { 'h', 'f' });
		NEARBY.put('h', new char[] { 'j', 'g' });
		NEARBY.put('j', new char[] { 'k', 'h' });
		NEARBY.put('k', new char[] { 'l', 'j' });
		NEARBY.put('l', new char[] { 'k', ';' });
		NEARBY.put('z', new char[] { 'x' });
		NEARBY.put('x', new char[] { 'z', 'c' });
		NEARBY.put('c', new char[] { 'x', 'v' });
		NEARBY.put('v', new char[] { 'c', 'b' });
		NEARBY.put('b', new char[] { 'v', 'n' });
		NEARBY.put('n', new char[] { 'b', 'm' });
		NEARBY.put('m', new char[] { 'n', '.' });
	};

	/**
	 * This method returns a string that has a random number of typos and types
	 * of typos in the string. This method can introduce the following problems:
	 * <ul>
	 * <li>same letter nearby on keyboard</li>
	 * <li>missed key, key not pressed</li>
	 * <li>transposed letter, keys pressed but not in right order</li>
	 * <li>double char, key pressed one time to many</li>
	 * <li>slash / accidently hit before pressing enter key</li>
	 * </ul>
	 * 
	 * @param s
	 * @return
	 */
	public String makeTypo(String s) {
		if (s.length() > 1) {
			boolean typoMade = false;
			double d = random.nextDouble();
			if (d < 0.4) {
				// Same letter nearby
				int pos1 = random.nextInt(s.length());
				char c1 = s.charAt(pos1);
				char[] nearby = NEARBY.get(Character.toLowerCase(c1));
				boolean upper = Character.isUpperCase(c1);
				if (nearby != null && nearby.length > 0) {
					c1 = nearby[random.nextInt(nearby.length)];
					c1 = upper ? Character.toUpperCase(c1) : Character
							.toLowerCase(c1);
				}
				s = s.substring(0, pos1) + c1 + s.substring(pos1 + 1);
				typoMade = true;
			}
			d = random.nextDouble();
			if (d < (typoMade ? 0.02 : 0.7)) {
				// missed key
				int pos1 = random.nextInt(s.length());
				s = s.substring(0, pos1) + s.substring(pos1 + 1);
				typoMade = true;
			}
			d = random.nextDouble();
			if (d < (typoMade ? 0.02 : 0.7)) {
				// transposed char
				int pos1 = random.nextInt(s.length() - 1);
				int pos2 = pos1 + 1;
				char c1 = s.charAt(pos1);
				char c2 = s.charAt(pos2);
				boolean upper1 = c1 > 'z';
				boolean upper2 = c2 > 'z';
				char t = c1;
				c1 = upper1 ? Character.toUpperCase(c2) : Character
						.toLowerCase(c2);
				c2 = upper2 ? Character.toUpperCase(t) : Character
						.toLowerCase(t);
				s = s.substring(0, pos1) + c1 + c2 + s.substring(pos2 + 1);
				typoMade = true;
			}
			d = random.nextDouble();
			if (d < (typoMade ? 0.02 : 0.9)) {
				// double char
				int pos1 = random.nextInt(s.length());
				char c1 = s.charAt(pos1);
				s = s.substring(0, pos1) + c1 + c1 + s.substring(pos1 + 1);
				typoMade = true;
			}
			d = random.nextDouble();
			if (d < 0.01 || !typoMade) {
				s = s + "\\";
			}
		}
		return s;
	}

	public static void main(String[] args) {
		String[] values = { "Hank", "Henry", "Charles", "Hank", "Henry",
				"Charles", "Hank", "Henry", "Charles" };
		Transformer transformer = new Transformer();
		Typest typest = new Typest(transformer);
		for (String value : values) {
			System.out.println(" + " + value + " = " + typest.makeTypo(value));
		}
	}

}
