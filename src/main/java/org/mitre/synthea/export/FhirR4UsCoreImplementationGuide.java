package org.mitre.synthea.export;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import org.hl7.fhir.r4.model.*;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.geography.Location;

import java.util.HashMap;
import java.util.Map;

public class FhirR4UsCoreImplementationGuide implements FhirR4Specialisation {

  //region UTILITIES

  private static final Table<String, String, String> US_CORE_MAPPING =
      FhirR4.loadMapping("us_core_mapping.csv");

  @SuppressWarnings("rawtypes")
  private static final Map raceEthnicityCodes = loadRaceEthnicityCodes();

  @SuppressWarnings({"rawtypes", "DuplicatedCode"})
  protected static Map loadRaceEthnicityCodes() {
    String filename = "race_ethnicity_codes.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      return g.fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  //endregion

  //region BASIC_INFO

  @Override
  public Patient basicInfoExtension(Patient patientResource, Person person,
                                    long stopTime) {

    patientResource = basicInfoForbidden(patientResource);

    //meta: this resource conforms to the US Core IG
    patientResource.setMeta(getConformanceToProfileMeta(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"));

    addRace(person, patientResource);
    addBirthSex(person, patientResource);
    modifyState(person, patientResource);

    return patientResource;
  }

  /**
   * IG requires that the state in the adress is given as a 2-char code for the US
   * project the state name to the standard abbreviation
   *
   * @param person          the person to render
   * @param patientResource the resource to modify
   */
  private void modifyState(Person person, Patient patientResource) {
    String state = (String) person.attributes.get(Person.STATE);
    state = Location.getAbbreviation(state);
    Address addrResource = patientResource.getAddressFirstRep();
    addrResource.setState(state);

  }

  private void addBirthSex(Person person, Patient patientResource) {
    Extension birthSexExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex");
    if (person.attributes.get(Person.GENDER).equals("M")) {
      patientResource.setGender(Enumerations.AdministrativeGender.MALE);
      birthSexExtension.setValue(new CodeType("M"));
    } else if (person.attributes.get(Person.GENDER).equals("F")) {
      patientResource.setGender(Enumerations.AdministrativeGender.FEMALE);
      birthSexExtension.setValue(new CodeType("F"));
    }
    patientResource.addExtension(birthSexExtension);
  }

  private void addRace(Person person, Patient patientResource) {
    // We do not yet account for mixed race
    Extension raceExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race");
    String race = (String) person.attributes.get(Person.RACE);

    String raceDisplay;
    switch (race) {
      case "white":
        raceDisplay = "White";
        break;
      case "black":
        raceDisplay = "Black or African American";
        break;
      case "asian":
        raceDisplay = "Asian";
        break;
      case "native":
        raceDisplay = "American Indian or Alaska Native";
        break;
      default: // Other (Put Hawaiian and Pacific Islander here for now)
        raceDisplay = "Other";
        break;
    }

    String raceNum = (String) raceEthnicityCodes.get(race);

    Extension raceCodingExtension = new Extension("ombCategory");
    Coding raceCoding = new Coding();
    if (raceDisplay.equals("Other")) {
      raceCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-NullFlavor");
      raceCoding.setCode("UNK");
      raceCoding.setDisplay("Unknown");
    } else {
      raceCoding.setSystem("urn:oid:2.16.840.1.113883.6.238");
      raceCoding.setCode(raceNum);
      raceCoding.setDisplay(raceDisplay);
    }
    raceCodingExtension.setValue(raceCoding);
    raceExtension.addExtension(raceCodingExtension);

    Extension raceTextExtension = new Extension("text");
    raceTextExtension.setValue(new StringType(raceDisplay));
    raceExtension.addExtension(raceTextExtension);
    patientResource.addExtension(raceExtension);

    // We do not yet account for mixed ethnicity
    Extension ethnicityExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity");
    String ethnicity = (String) person.attributes.get(Person.ETHNICITY);

    String ethnicityDisplay;
    if (ethnicity.equals("hispanic")) {
      ethnicity = "hispanic";
      ethnicityDisplay = "Hispanic or Latino";
    } else {
      ethnicity = "nonhispanic";
      ethnicityDisplay = "Not Hispanic or Latino";
    }

    String ethnicityNum = (String) raceEthnicityCodes.get(ethnicity);

    Extension ethnicityCodingExtension = new Extension("ombCategory");
    Coding ethnicityCoding = new Coding();
    ethnicityCoding.setSystem("urn:oid:2.16.840.1.113883.6.238");
    ethnicityCoding.setCode(ethnicityNum);
    ethnicityCoding.setDisplay(ethnicityDisplay);
    ethnicityCodingExtension.setValue(ethnicityCoding);

    ethnicityExtension.addExtension(ethnicityCodingExtension);
    Extension ethnicityTextExtension = new Extension("text");
    ethnicityTextExtension.setValue(new StringType(ethnicityDisplay));
    ethnicityExtension.addExtension(ethnicityTextExtension);
    patientResource.addExtension(ethnicityExtension);
  }

  //endregion

  //region ENCOUNTER

  @Override
  public Encounter encounterExtension(Encounter encounterResource, Person person,
                                      Patient patientResource,
                                      Bundle bundle,
                                      HealthRecord.Encounter encounter) {

    encounterResource = encounterForbidden(encounterResource);

    //meta: conforms to IG
    encounterResource.setMeta(getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter"));

    //add location to the encounter
    Provider provider = encounter.provider != null ?
        encounter.provider :
        person.getProvider(HealthRecord.EncounterType.WELLNESS, encounter.start); // no associated provider, patient goes to wellness provider
    encounterResource.addLocation().setLocation(new Reference()
        .setReference(FhirR4.findLocationUrl(provider, bundle))
        .setDisplay(provider.name));

    // US Core Encounters should have an identifier to support the required
    // Encounter.identifier search parameter
    encounterResource.addIdentifier()
        .setUse(Identifier.IdentifierUse.OFFICIAL)
        .setSystem("https://github.com/synthetichealth/synthea")
        .setValue(encounterResource.getId());

    return encounterResource;
  }

  //endregion

  //region CONDITION

  @Override
  public Condition conditionExtension(Condition conditionResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry condition) {

    conditionResource = conditionForbidden(conditionResource);

    conditionResource.setMeta(getConformanceToProfileMeta(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition"));
    conditionResource.addCategory(new CodeableConcept().addCoding(new Coding(
        "http://terminology.hl7.org/CodeSystem/condition-category", "encounter-diagnosis",
        "Encounter Diagnosis")));

    return conditionResource;
  }

  //endregion

  //region ALLERGY

  @Override
  public AllergyIntolerance allergyExtension(AllergyIntolerance allergyResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry allergy) {
    allergyResource = allergyForbidden(allergyResource);
    allergyResource.setMeta(getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-allergyintolerance"));
    return allergyResource;
  }

  //endregion

  //region OBSERVATION

  @Override
  public Observation observationExtension(Observation observationResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Observation observation) {

    observationResource = observationForbidden(observationResource);

    Meta meta = new Meta();
    // add the specific profile based on code
    HealthRecord.Code code = observation.codes.get(0);
    String codeMappingUri = US_CORE_MAPPING != null ? US_CORE_MAPPING.get(FhirR4.LOINC_URI, code.code) : null;
    if (codeMappingUri != null) {
      meta.addProfile(codeMappingUri);
      if (!codeMappingUri.contains("/us/core/") && observation.category.equals("vital-signs")) {
        meta.addProfile("http://hl7.org/fhir/StructureDefinition/vitalsigns");
      }
    } else if (observation.report != null && observation.category.equals("laboratory")) {
      meta.addProfile("http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab");
    }
    if (meta.hasProfile()) {
      observationResource.setMeta(meta);
    }

    return observationResource;
  }

  //endregion

  //region PROCEDURE

  @Override
  public Procedure procedureExtension(Procedure procedureResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Procedure procedure) {
    procedureResource = procedureForbidden(procedureResource);
    procedureResource.setMeta(getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-procedure"));

    //add the location of the procedure
    Encounter encounterResource = (Encounter) encounterEntry.getResource();
    procedureResource.setLocation(encounterResource.getLocationFirstRep().getLocation());

    return procedureResource;
  }

  //endregion

  //region DEVICE
  @Override
  public Device deviceExtension(Device deviceResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, HealthRecord.Device device) {
    deviceResource = deviceForbidden(deviceResource);
    deviceResource.setMeta(getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-implantable-device"));
    return deviceResource;
  }
  //endregion

}
