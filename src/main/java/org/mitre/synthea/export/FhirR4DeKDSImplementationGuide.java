package org.mitre.synthea.export;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import javax.print.DocFlavor;
import javax.xml.crypto.Data;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extension for the FhirR4 exporter, producing resource bundles conforming to the
 * Core Dataset IG of the German MI-I (https://simplifier.net/medizininformatikinitiative-kerndatensatz)
 */
public class FhirR4DeKDSImplementationGuide implements FhirR4Specialisation {

  private static final String SYSTEM_IDENTIFIER_TYPE_DE_BASIS = "http://fhir.de/CodeSystem/identifier-type-de-basis";
  private static final String SYNTHEA_DE = "https://github.com/itcr-uni-luebeck/synthea";
  private static final String NAMINGSYSTEM_GKV_KVID10 = "http://fhir.de/NamingSystem/gkv/kvid-10";
  private static final String NAMINGSYSTEM_ARGE_IK_IKNR = "http://fhir.de/NamingSystem/arge-ik/iknr";
  private static final String SYSTEM_V2_0203 = "http://terminology.hl7.org/CodeSystem/v2-0203";
  private static final String EXTENSION_DE_HUMANNAME_NAMENSZUSATZ = "http://fhir.de/StructureDefinition/humanname-namenszusatz";
  private static final String EXTENSION_DE_HUMANNAME_OWNNAME = "http://hl7.org/fhir/StructureDefinition/humanname-own-name";
  private static final String EXTENSION_DE_HUMANNAME_OWNPREFIX = "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix";
  private static final String EXTENSION_PREFIX_QUALIFIER = "http://hl7.org/fhir/StructureDefinition/iso21090-EN-qualifier";
  private static final HashMap<String, String> ADDITIONAL_ATTRIBUTE_MAP = new HashMap<>();
  private static final String SYSTEM_GENDER_AMTLICH_DE = "http://fhir.de/CodeSystem/gender-amtlich-de";
  private static final String EXTENSION_GENDER_AMTLICH_DE = "http://fhir.de/StructureDefinition/gender-amtlich-de";
  private static final String EXTENSION_DATA_ABSENT_REASON = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
  private static final String EXTENSION_ADXP_POSTBOX = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-postBox";
  private static final String EXTENSION_ADXP_ADDITIONAL_LOCATOR = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator";
  private static final String EXTENSION_ADXP_HOUSENUMBER = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber";
  private static final String EXTENSION_ADXP_STREETNAME = "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName";
  private static final String EXTENSION_DESTATIS_AGS = "http://fhir.de/StructureDefinition/destatis/ags";
  private static final String SYSTEM_DESTATIS_AGS = "http://fhir.de/NamingSystem/destatis/ags";
  private static final String[] ADDITONAL_ADDRESS_TYPES = new String[]{"Unit", "Apt", "Suite", "Apartment", "Appartement", "Stockwerk"};
  private static final String[] STREET_TYPES = new String[]{"-Stra\\we", " Stra\\we", "-Str.", " Allee", "-Weg", " Platz"};
  /**
   * split the address into the parts 'house number' (no), 'street' (stn and stt) and additional line
   * for unit/apt/suite,... (adt and adn)
   */
  private static final Pattern PATTERN_SPLIT_ADDRESS = Pattern.compile(
      "(?<no>\\d*) (?<stn>\\w*) (?<stt>(?:" +
          String.join("|", STREET_TYPES) +
          "))(?:$| (?<adt>" +
          String.join("|", ADDITONAL_ADDRESS_TYPES) +
          ") (?<adn>\\d*))",
      Pattern.UNICODE_CHARACTER_CLASS
  );
  private final Map<String, String> AGS_MAP = buildAgsMap();

  public HashMap<String, String> buildAgsMap() {
    String filename = Config.get("generate.geography.zipcodes.default_file");
    HashMap<String, String> agsMap = new HashMap<>();
    try {
      String csv = Utilities.readResource(filename);
      List<? extends Map<String, String>> zipCsv = SimpleCSV.parse(csv);
      zipCsv.forEach(row -> agsMap.put(row.get("ZCTA5"), row.get("")));
      return agsMap;
    } catch (Exception e) {
      return agsMap;
    }
  }

  @Override
  public boolean handles(ResourceType resourceType) {
    switch (resourceType) {
      case BASIC_INFO:
      case ENCOUNTER:
      case CONDITION:
      case OBSERVATION:
      case PROCEDURE:
      case MEDICATION_REQUEST:
        return true;
      default:
        return false; //DE KDS does not handle as many fields as the US Core IG
    }
  }

  @Override
  public void beforeExport(Person person) {
    writeToConsole("started exporting", person);
  }

  @Override
  public void afterExport(Person person) {
    writeToConsole("done exporting", person);
  }

  private void writeToConsole(String s, Person person, Object... o) {
    var given = person.attributes.get(Person.FIRST_NAME).toString().substring(0, 2);
    var family = person.attributes.get(Person.LAST_NAME).toString().substring(0, 2);
    var prefix = String.format("[%s-%s] :  ", given, family);
    var message = String.format(s, o);
    writeToConsole(prefix, message);
  }

  private void writeToConsole(String prefix, String message) {
    System.out.print(prefix);
    System.out.println(message);
    System.out.flush();
  }

  @Override
  public Patient basicInfoExtension(Patient patientResource, Person person, //TODO [WIP JW 2020-06-03]
                                    long stopTime) {
    patientResource = basicInfoForbidden(patientResource);
    patientResource.setMeta(
        FhirR4Specialisation
            .getConformanceToProfileMeta("https://www.medizininformatik-initiative.de/fhir/core/StructureDefinition/Patient")
            .setSource(SYNTHEA_DE));
    basicInfoSetPatientIdentifiers(patientResource, person);
    basicInfoSetName(patientResource, person);
    basicInfoSetGender(patientResource, person);
    basicInfoSetBirthdayDataAbsentReason(patientResource, person);
    basicInfoSetDeceasedBoolean(patientResource, person);
    basicInfoSetAddress(patientResource, person);

    return patientResource;
  }

  private void basicInfoSetAddress(Patient patientResource, Person person) {
    var address = patientResource.getAddressFirstRep();
    var line = address.getLine().get(0);
    address.setUse(Address.AddressUse.HOME);
    if (new BooleanAdditionalAttribute(0.2f, person.random).generate()) {
      //make this address a PO box
      address.setType(Address.AddressType.POSTAL);
      var poBoxNumber = "Postfach " + String.valueOf(person.random.nextInt(999999) + 1);
      var poBoxStringType = new StringType(poBoxNumber);
      line.setValue(poBoxNumber).addExtension().setUrl(EXTENSION_ADXP_POSTBOX).setValue(poBoxStringType);
      writeToConsole("Changed address to PO Box '%s'", person, poBoxNumber);
    } else {
      //this address remains a home address, add extensions for number, etc.
      //also, add the house number after the street name, conforming to German practise
      var splitAddress = PATTERN_SPLIT_ADDRESS.matcher(line.getValue());
      if (!splitAddress.matches()) {
        throw new FHIRException("Address does not conform to pattern!");
      }
      String houseNumber = splitAddress.group("no");
      String streetName = splitAddress.group("stn");
      String streetType = splitAddress.group("stt");
      String additionalType = splitAddress.group("adt");
      String additionalNo = splitAddress.group("adn");
      String street = String.format("%s%s", streetName, streetType);
      line.addExtension(EXTENSION_ADXP_HOUSENUMBER, new StringType(houseNumber));
      line.addExtension(EXTENSION_ADXP_STREETNAME, new StringType(street));
      var lineBuilder = new StringBuilder();
      lineBuilder.append(street).append(" ").append(houseNumber);
      if (additionalType != null) {
        String additional = String.format("%s %s", additionalType, additionalNo);
        lineBuilder.append(", ").append(additional);
        line.addExtension(EXTENSION_ADXP_ADDITIONAL_LOCATOR, new StringType(additional));
      }
      line.setValue(lineBuilder.toString());
      writeToConsole("reformatted address to %s", person, lineBuilder.toString());
    }

    //add the Amtlicher Gemeindeschluessel to the city in some instances
    if (new BooleanAdditionalAttribute(0.1f, person.random).generate()) {
      var zip = address.getPostalCode();
      if (AGS_MAP.containsKey(zip)) {
        var ags = AGS_MAP.get(zip);
        var agsCoding = new Coding().setSystem(SYSTEM_DESTATIS_AGS).setCode(ags);
        address.getCityElement().addExtension(EXTENSION_DESTATIS_AGS, agsCoding);
        writeToConsole("added AGS %s", person, ags);
      }
    }
  }

  /**
   * systems must support deceasedBoolean as well as deceasedDateTime.
   * Change some deceasedDateTime to deceasedBoolean
   *
   * @param patientResource the resource to modify
   * @param person          the person being rendered
   */
  private void basicInfoSetDeceasedBoolean(Patient patientResource, Person person) {
    var change = new BooleanAdditionalAttribute(person.random).generate();
    if (change) {
      if (patientResource.getDeceased() != null) {
        patientResource.setDeceased(new BooleanType(true));
        writeToConsole("changed deceasedDateTime to deceasedBoolean", person);
      } else {
        patientResource.setDeceased(new BooleanType(false));
        writeToConsole("set deceasedBoolean to FALSE", person);
      }
    }
  }

  private void basicInfoSetBirthdayDataAbsentReason(Patient patientResource, Person person) {
    //systems must support the data absent reason extension for the birthday
    if (new BooleanAdditionalAttribute(Chances.BIRTHDAY_MISSING, person.random).generate()) {
      var dataAbsentReason = new GenericListAdditionalAttribute<>(0.0f, person.random,
          DataAbsentReason.ASKEDDECLINED, DataAbsentReason.ASKEDUNKNOWN, DataAbsentReason.UNKNOWN).generate();
      var absentBirthdate = new DateType();
      absentBirthdate.addExtension()
          .setUrl(EXTENSION_DATA_ABSENT_REASON)
          .setValue(new CodeType(dataAbsentReason.toCode()));
      patientResource.setBirthDateElement(absentBirthdate);
      writeToConsole("removed birthday, reason: %s", person, dataAbsentReason.getDisplay());
    }
  }

  private void basicInfoSetGender(Patient patientResource, Person person) {
    //synthea, by default, does not consider non-binary genders.
    //because the KDS Core IG does make provisions for the third gender,
    //some patients will be mapped to other genders
    if (new BooleanAdditionalAttribute(Chances.GENDER_CHANGE, person.random).generate()) {
      //50:50 ration between other[divers] and other[unbestimmt]
      var isDiverse = new BooleanAdditionalAttribute(person.random).generate();
      Coding genderAmtlichCoding = new Coding()
          .setSystem(SYSTEM_GENDER_AMTLICH_DE)
          .setCode(isDiverse ? "D" : "X")
          .setDisplay(isDiverse ? "divers" : "unbestimmt");
      writeToConsole("Gender mapped to OTHER[%s = %s]", person, genderAmtlichCoding.getCode(), genderAmtlichCoding.getDisplay());
      patientResource.setGender(Enumerations.AdministrativeGender.OTHER);
      patientResource.getGenderElement().addExtension(EXTENSION_GENDER_AMTLICH_DE, genderAmtlichCoding);
    }
  }

  private void basicInfoSetName(Patient patientResource, Person person) {
    patientResource.getName()
        .forEach(hn -> {
          // from the implementation guide 'Leitfaden Basis DE (R4)':
          // "Anreden sollten, sofern erforderlich, ausschließlich in HumanName.text erscheinen
          // (z.B. 'Frau Dr. Martha Musterfrau'). Nach Möglichkeit sollte die Anrede aus dem Geschlecht
          // der Person abgeleitet werden."
          hn.getPrefix().removeIf(p -> p.getValue().equals("Mr.") || p.getValue().equals("Mrs.") || p.getValue().equals("Ms."));

          //generally, academic titles are prefixes in DE, not suffixes like MD
          //prefixes should support a qualifier
          if (hn.hasSuffix()) {
            var extensionAc = new Extension()
                .setUrl(EXTENSION_PREFIX_QUALIFIER)
                .setValue(new CodeType("AC"));
            //a 'PhD' suffix is possible in Germany, but uncommon. Map some instances to "Dr." or "Dr. phil."
            StringType prefix = null;
            if (hn.hasSuffix("PhD") && new BooleanAdditionalAttribute(Chances.MAP_PHD, person.random).generate()) {
              prefix = new StringListAdditionalAttribute(0f, person.random,
                  "Dr. Phil.", "Dr. rer. nat.", "Dr.", "Dr. Dr. h.c.")
                  .generateAsStringType();
              hn.getSuffix().removeIf(s -> s.getValue().equals("PhD"));
            }
            if (hn.hasSuffix("JD")) {
              prefix = new StringListAdditionalAttribute(0.0f, person.random,
                  "Dr. jur.", "Dr.").generateAsStringType();
              hn.getSuffix().removeIf(s -> s.getValue().equals("JD"));
            }
            //some persons may have foreign medical degrees
            if (hn.hasSuffix("MD") && new BooleanAdditionalAttribute(Chances.MAP_MD, person.random).generate()) {
              prefix = new StringListAdditionalAttribute(0.0f, person.random,
                  "Dr. med.", "Dr. dent.", "Dr.").generateAsStringType();
              hn.getSuffix().removeIf(s -> s.getValue().equals("MD"));
            }
            if (prefix != null) {
              prefix.addExtension(extensionAc);
              hn.getPrefix().add(prefix);
            }
            writeToConsole("  Suffix mapped, name is now %s", hn.getNameAsSingleString());
          }
        });

    //nobility may require additional name components
    //there are no official statistics available regarding the distribution of nobility names in Germany available.
    //the chance of 0.1 is likely over-estimated, but some names feature voorvoegsel without prefixes
    if (new BooleanAdditionalAttribute(Chances.IS_NOBILITY, person.random).generate()) {
      var isFemale = person.attributes.get(Person.GENDER).equals("F");
      var officialName = patientResource.getName().stream().filter(p -> p.getUse().equals(HumanName.NameUse.OFFICIAL)).findFirst().orElseThrow();
      //voorvoegsel are (almost?) always featured in "nobility" names
      //https://de.wikipedia.org/wiki/Adelspr%C3%A4dikat#Deutschland
      var ownPrefix = new StringListAdditionalAttribute(1 - Chances.NOBILITY_HAS_VOORVOEGSEL, person.random,
          "von", "v.", "von und zu", "vom", "zum", "vom und zum", "von der", "von dem", "de", "van", "van der"
      ).generateAsStringType();

      //prefixes ("Prinz") are rather uncommon today
      //https://de.wikipedia.org/wiki/Adelstitel#%C3%9Cberblick:_Adelstitel_und_Adelsr%C3%A4nge
      var namenszusatz = new GenderedStringListAdditionalAttribute(1 - Chances.NOBILITY_HAS_PREFIX, person.random, isFemale,
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Prinz", "Prinzessin"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Kurfürst", "Kurfürstin"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Herzog", "Herzogin"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Fürst", "Fürstin"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Graf", "Gräfin"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Freiherr", "Freifrau"),
          new GenderedStringListAdditionalAttribute.GenderedStringChoice("Baron", "Baronin")).generateAsStringType();

      if (ownPrefix != null || namenszusatz != null) {
        StringBuilder famNameBuilder = new StringBuilder();
        var famName = officialName.getFamily();
        officialName.getFamilyElement().addExtension(EXTENSION_DE_HUMANNAME_OWNNAME, new StringType(famName));

        if (namenszusatz != null) {
          officialName.getFamilyElement().addExtension(EXTENSION_DE_HUMANNAME_NAMENSZUSATZ, namenszusatz);
          famNameBuilder.append(namenszusatz.getValue()).append(" ");
        }
        if (ownPrefix != null) {
          officialName.getFamilyElement().addExtension(EXTENSION_DE_HUMANNAME_OWNPREFIX, ownPrefix);
          famNameBuilder.append(ownPrefix.getValue()).append(" ");
        }
        famNameBuilder.append(famName);
        officialName.getFamilyElement().setValue(famNameBuilder.toString());
      }
      writeToConsole("  Nobility, name is now %s", officialName.getNameAsSingleString());
    }
  }

  private void basicInfoSetPatientIdentifiers(Patient patientResource, Person person) {
    //generic implementation does not set a use for the Medical Record Number
    patientResource.getIdentifier().stream()
        .filter(i -> i.getType().hasCoding(SYSTEM_V2_0203, "MR"))
        .forEach(i -> {
          i.setUse(Identifier.IdentifierUse.USUAL);
          i.getAssigner().setReference(SYNTHEA_DE); //TODO PID identifier req's a reference to an assigner. Use something else?
        });

    if (person.attributes.get(Person.IDENTIFIER_PASSPORT) != null) {
      //passport no has 10 digits and can be used as a substitute for the GKV identifier
      HealthRecord.Code gkvCode = new HealthRecord.Code(SYSTEM_IDENTIFIER_TYPE_DE_BASIS,
          "GKV", "Gesetzliche Krankenversicherung");
      var identifierGkv = new Identifier().setType(FhirR4.mapCodeToCodeableConcept(gkvCode, null))
          .setSystem(NAMINGSYSTEM_GKV_KVID10)
          .setValue((String) person.attributes.get(Person.IDENTIFIER_PASSPORT))
          .setUse(Identifier.IdentifierUse.OFFICIAL);
      var xxType = new HealthRecord.Code(SYSTEM_V2_0203, "XX", "Organisations-ID");
      identifierGkv.getAssigner().setIdentifier(new Identifier()
          .setUse(Identifier.IdentifierUse.OFFICIAL)
          .setType(FhirR4.mapCodeToCodeableConcept(xxType, null))
          .setSystem(NAMINGSYSTEM_ARGE_IK_IKNR)
          .setValue("123456789"));
      patientResource.addIdentifier(identifierGkv);
    }

    if (person.attributes.get(Person.IDENTIFIER_SSN) != null) {
      //use Social Security Number as a substitute for the private insurance number
      HealthRecord.Code pkvCode = new HealthRecord.Code(SYSTEM_IDENTIFIER_TYPE_DE_BASIS, "PKV", "Private Krankenversicherung");
      Identifier pkvIdentifier = new Identifier().setType(FhirR4.mapCodeToCodeableConcept(pkvCode, null))
          .setValue((String) person.attributes.get(Person.IDENTIFIER_SSN))
          .setUse(Identifier.IdentifierUse.SECONDARY);
      pkvIdentifier.getAssigner().setDisplay("Privates Krankenversicherungsunternehmen");
      patientResource.addIdentifier(pkvIdentifier);
    }
  }

  /**
   * remove data elements that do not make sense within the context of the MII.
   * In particular, driver's ID and passport# are not usually used in Germany.
   *
   * @param patientResource the resource to remove from
   * @return the cleaned-up resource
   */
  @Override
  public Patient basicInfoForbidden(Patient patientResource) {
    var filteredIdentifiers = patientResource.getIdentifier().stream().filter(i ->
        i.getType().hasCoding("http://terminology.hl7.org/CodeSystem/v2-0203", "MR")
            || (i.hasSystem() && i.getSystem().equals("https://github.com/synthetichealth/synthea")))
        .collect(Collectors.toList()); //allow medical record number and internal ID
    patientResource.getIdentifier().clear();
    patientResource.getIdentifier().addAll(filteredIdentifiers);

    return patientResource;
  }

  @Override
  public void bundleExtensions(Bundle bundle, Person person, Patient patientEntry, long stopTime) {
    //nyi
  }

  @Override
  public void encounterExtensions(Encounter encounterResource, Bundle.BundleEntryComponent encounterComponent, HealthRecord.Encounter encounterModel, Patient patientResource, Bundle.BundleEntryComponent patientComponent, Person patientAgent, Bundle bundle) {
    //nyi
  }

  private static class Chances {
    static final float BIRTHDAY_MISSING = 0.1f;
    static final float GENDER_CHANGE = 0.1f;
    static final float MAP_PHD = 0.5f;
    static final float MAP_MD = 0.5f;
    static final float IS_NOBILITY = 0.3f;
    static final float NOBILITY_HAS_VOORVOEGSEL = 0.95f;
    static final float NOBILITY_HAS_PREFIX = 0.25f;
  }


}
