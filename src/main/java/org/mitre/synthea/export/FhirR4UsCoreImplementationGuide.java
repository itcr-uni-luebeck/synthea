package org.mitre.synthea.export;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.hl7.fhir.r4.model.*;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.geography.Location;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.mitre.synthea.export.FhirR4.*;

public class FhirR4UsCoreImplementationGuide implements FhirR4Specialisation {

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

  /**
   * Map the Provider into a FHIR Location resource, and add it to the given Bundle.
   *
   * @param bundle   The Bundle to add to
   * @param provider The Provider
   * @return The added Entry
   */
  private static Bundle.BundleEntryComponent providerLocation(Bundle bundle, Provider provider) {
    org.hl7.fhir.r4.model.Location location = new org.hl7.fhir.r4.model.Location();
    location.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-location"));

    location.setStatus(org.hl7.fhir.r4.model.Location.LocationStatus.ACTIVE);
    location.setName(provider.name);
    // set telecom
    if (provider.phone != null && !provider.phone.isEmpty()) {
      ContactPoint contactPoint = new ContactPoint()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue(provider.phone);
      location.addTelecom(contactPoint);
    } else {
      ContactPoint contactPoint = new ContactPoint()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue("(555) 555-5555");
      location.addTelecom(contactPoint);
    }
    // set address
    Address address = new Address()
        .addLine(provider.address)
        .setCity(provider.city)
        .setPostalCode(provider.zip)
        .setState(provider.state);
    if (FhirR4.COUNTRY_CODE != null) {
      address.setCountry(FhirR4.COUNTRY_CODE);
    }
    location.setAddress(address);
    org.hl7.fhir.r4.model.Location.LocationPositionComponent position = new org.hl7.fhir.r4.model.Location.LocationPositionComponent();
    position.setLatitude(provider.getY());
    position.setLongitude(provider.getX());
    location.setPosition(position);
    location.setManagingOrganization(new Reference()
        .setReference(FhirR4.getUrlPrefix("Organization") + provider.getResourceID())
        .setDisplay(provider.name));
    return newEntry(bundle, location);
  }

  /**
   * Create a Provenance entry at the end of this Bundle that
   * targets all the entries in the Bundle.
   *
   * @param bundle   The finished complete Bundle.
   * @param person   The person.
   * @param stopTime The time the simulation stopped.
   */
  private static void provenance(Bundle bundle, Person person, long stopTime) {
    Provenance provenance = new Provenance();
    provenance.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-provenance"));

    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      provenance.addTarget(new Reference(entry.getFullUrl()));
    }
    provenance.setRecorded(new Date(stopTime));

    // Provenance sources...
    int last = person.record.encounters.size() - 1;
    Clinician clinician = person.record.encounters.get(last).clinician;
    String practitionerFullUrl = FhirR4.findPractitioner(clinician, bundle);
    Provider providerOrganization = person.record.provider;
    if (providerOrganization == null) {
      providerOrganization = person.getProvider(HealthRecord.EncounterType.WELLNESS, stopTime);
    }
    String organizationFullUrl = FhirR4.findProviderUrl(providerOrganization, bundle);

    // Provenance Author...
    Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
    agent.setType(FhirR4.mapCodeToCodeableConcept(
        new HealthRecord.Code("http://terminology.hl7.org/CodeSystem/provenance-participant-type",
            "author", "Author"), null));
    agent.setWho(new Reference()
        .setReference(practitionerFullUrl)
        .setDisplay(clinician.getFullname()));
    agent.setOnBehalfOf(new Reference()
        .setReference(organizationFullUrl)
        .setDisplay(providerOrganization.name));

    // Provenance Transmitter...
    agent = provenance.addAgent();
    agent.setType(FhirR4.mapCodeToCodeableConcept(
        new HealthRecord.Code("http://hl7.org/fhir/us/core/CodeSystem/us-core-provenance-participant-type",
            "transmitter", "Transmitter"), null));
    agent.setWho(new Reference()
        .setReference(practitionerFullUrl)
        .setDisplay(clinician.getFullname()));
    agent.setOnBehalfOf(new Reference()
        .setReference(organizationFullUrl)
        .setDisplay(providerOrganization.name));
    newEntry(bundle, provenance);
  }

  @Override
  public boolean handles(ResourceType resourceType) {
    return true; //US CORE IG handles everything
  }

  @Override
  public Patient basicInfoExtension(Patient patientResource, Person person,
                                    long stopTime) {

    //meta: this resource conforms to the US Core IG
    patientResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta(
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

  @Override
  public Encounter encounterExtension(Encounter encounterResource, Person person,
                                      Patient patientResource,
                                      Bundle bundle,
                                      HealthRecord.Encounter encounter, Bundle.BundleEntryComponent encounterComponent) {
    //meta: conforms to IG
    encounterResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter"));

    //add location to the encounter
    Provider provider = encounter.provider != null ?
        encounter.provider :
        person.getProvider(HealthRecord.EncounterType.WELLNESS, encounter.start); // no associated provider, patient goes to wellness provider
    encounterResource.addLocation().setLocation(new Reference()
        .setReference(findLocationUrl(provider, bundle))
        .setDisplay(provider.name));

    // US Core Encounters should have an identifier to support the required
    // Encounter.identifier search parameter
    encounterResource.addIdentifier()
        .setUse(Identifier.IdentifierUse.OFFICIAL)
        .setSystem("https://github.com/synthetichealth/synthea")
        .setValue(encounterComponent.getResource().getId());

    return encounterResource;
  }

  @Override
  public Condition conditionExtension(Condition conditionResource,
                                      Bundle.BundleEntryComponent personEntry,
                                      Bundle bundle,
                                      Bundle.BundleEntryComponent encounterEntry,
                                      HealthRecord.Entry condition) {
    conditionResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition"));
    conditionResource.addCategory(new CodeableConcept().addCoding(new Coding(
        "http://terminology.hl7.org/CodeSystem/condition-category", "encounter-diagnosis",
        "Encounter Diagnosis")));

    return conditionResource;
  }

  @Override
  public AllergyIntolerance allergyExtension(AllergyIntolerance allergyResource,
                                             Bundle.BundleEntryComponent personEntry,
                                             Bundle bundle,
                                             Bundle.BundleEntryComponent encounterEntry,
                                             HealthRecord.Entry allergy) {
    allergyResource = allergyForbidden(allergyResource);
    allergyResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-allergyintolerance"));
    return allergyResource;
  }

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

  @Override
  public Procedure procedureExtension(Procedure procedureResource,
                                      Bundle.BundleEntryComponent personEntry,
                                      Bundle bundle,
                                      Bundle.BundleEntryComponent encounterEntry,
                                      HealthRecord.Procedure procedure) {
    procedureResource = procedureForbidden(procedureResource);
    procedureResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-procedure"));

    //add the location of the procedure
    Encounter encounterResource = (Encounter) encounterEntry.getResource();
    procedureResource.setLocation(encounterResource.getLocationFirstRep().getLocation());

    return procedureResource;
  }

  @Override
  public Device deviceExtension(Device deviceResource,
                                Bundle.BundleEntryComponent personEntry,
                                Bundle bundle,
                                HealthRecord.Device device) {
    deviceResource = deviceForbidden(deviceResource);
    deviceResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-implantable-device"));
    return deviceResource;
  }

  @Override
  public MedicationRequest medicationRequestExtension(MedicationRequest medicationRequest,
                                                      Person person,
                                                      Bundle.BundleEntryComponent personEntry,
                                                      Bundle bundle,
                                                      Bundle.BundleEntryComponent encounterEntry,
                                                      HealthRecord.Medication medication) {
    medicationRequest.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-medicationrequest"));
    if (medication.administration) {
      // Occasionally, rather than use medication codes, we want to use a Medication
      // Resource. We only want to do this when we use US Core, to make sure we
      // sometimes produce a resource for the us-core-medication profile, and the
      // 'administration' flag is an arbitrary way to decide without flipping a coin.
      org.hl7.fhir.r4.model.Medication drugResource =
          new org.hl7.fhir.r4.model.Medication();
      drugResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-medication"));
      HealthRecord.Code code = medication.codes.get(0);
      String system = code.system.equals("SNOMED-CT")
          ? FhirR4.SNOMED_URI
          : FhirR4.RXNORM_URI;
      drugResource.setCode(FhirR4.mapCodeToCodeableConcept(code, system));
      drugResource.setStatus(Medication.MedicationStatus.ACTIVE);
      Bundle.BundleEntryComponent drugEntry = newEntry(bundle, drugResource);
      medicationRequest.setMedication(new Reference(drugEntry.getFullUrl()));
    }

    return medicationRequest;
  }

  @Override
  public Immunization immunizationExtension(Immunization immunizationResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry immunization) {
    immunizationResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-immunization"));
    org.hl7.fhir.r4.model.Encounter encounterResource =
        (org.hl7.fhir.r4.model.Encounter) encounterEntry.getResource();
    immunizationResource.setLocation(encounterResource.getLocationFirstRep().getLocation());
    return immunizationResource;
  }

  @Override
  public DiagnosticReport diagnosticReportExtension(DiagnosticReport diagnosticReport, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Report report) {
    diagnosticReport.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-lab"));
    org.hl7.fhir.r4.model.Encounter encounterResource =
        (org.hl7.fhir.r4.model.Encounter) encounterEntry.getResource();
    diagnosticReport.addPerformer(encounterResource.getServiceProvider());
    return diagnosticReport;
  }

  @Override
  public CareTeam careTeamExtension(CareTeam careTeam, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.CarePlan carePlan) {
    careTeam.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-careteam"));
    return careTeam;
  }

  @Override
  public CarePlan carePlanExtension(CarePlan carePlanResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, Provider provider, Bundle.BundleEntryComponent careTeamEntry, HealthRecord.CarePlan carePlan) {
    carePlanResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-careplan"));
    carePlanResource.addCategory(FhirR4.mapCodeToCodeableConcept(
        new HealthRecord.Code("http://hl7.org/fhir/us/core/CodeSystem/careplan-category",
            "assess-plan",
            null),
        null));
    return carePlanResource;
  }

  @Override
  public ImagingStudy imagingStudyExtension(ImagingStudy imagingStudyResource,
                                            Bundle.BundleEntryComponent personEntry,
                                            Bundle bundle,
                                            Bundle.BundleEntryComponent encounterEntry,
                                            HealthRecord.ImagingStudy imagingStudy) {
    org.hl7.fhir.r4.model.Encounter encounterResource =
        (org.hl7.fhir.r4.model.Encounter) encounterEntry.getResource();
    imagingStudyResource.setLocation(encounterResource.getLocationFirstRep().getLocation());

    return imagingStudyResource;
  }

  @Override
  public Claim encounterClaimExtension(Claim encounterClaimResource, Person person, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, org.mitre.synthea.world.concepts.Claim claim) {
    org.hl7.fhir.r4.model.Encounter encounterResource =
        (org.hl7.fhir.r4.model.Encounter) encounterEntry.getResource();
    encounterClaimResource.setFacility(encounterResource.getLocationFirstRep().getLocation());
    return encounterClaimResource;
  }

  @Override
  public ExplanationOfBenefit explanationOfBenefitExtension(ExplanationOfBenefit explanationResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, Person person, Bundle.BundleEntryComponent claimEntry, HealthRecord.Encounter encounter) {
    org.hl7.fhir.r4.model.Encounter encounterResource =
        (org.hl7.fhir.r4.model.Encounter) encounterEntry.getResource();
    explanationResource.setFacility(encounterResource.getLocationFirstRep().getLocation());
    return explanationResource;
  }

  @Override
  public Organization providerExtension(Organization providerResource, Bundle bundle, Provider provider) {
    providerResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-organization"));
    ContactPoint contactPoint = new ContactPoint()
        .setSystem(ContactPoint.ContactPointSystem.PHONE)
        .setValue("(555) 555-5555");
    providerResource.addTelecom(contactPoint);
    providerLocation(bundle, provider);
    return providerResource;
  }

  @Override
  public Practitioner practitionerExtension(Practitioner practitionerResource,
                                            Bundle bundle,
                                            Clinician clinician) {
    practitionerResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-practitioner"));

    practitionerResource.getTelecomFirstRep().addExtension()
        .setUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-direct")
        .setValue(new BooleanType(true));

    return practitionerResource;
  }

  @Override
  public void addPractitionerRole(Practitioner practitionerResource,
                                  Bundle.BundleEntryComponent practitionerEntry,
                                  Bundle bundle,
                                  Clinician clinician) {
    // generate an accompanying PractitionerRole resource
    PractitionerRole practitionerRole = new PractitionerRole();
    Meta meta = new Meta();
    meta.addProfile(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-practitionerrole");
    practitionerRole.setMeta(meta);
    practitionerRole.setPractitioner(new Reference()
        .setReference(practitionerEntry.getFullUrl())
        .setDisplay(practitionerResource.getNameFirstRep().getNameAsSingleString()));
    practitionerRole.setOrganization(new Reference()
        .setReference(
            getUrlPrefix("Organization") + clinician.getOrganization().getResourceID())
        .setDisplay(clinician.getOrganization().name));
    practitionerRole.addCode(
        mapCodeToCodeableConcept(
            new HealthRecord.Code("http://nucc.org/provider-taxonomy", "208D00000X", "General Practice"),
            null));
    practitionerRole.addSpecialty(
        mapCodeToCodeableConcept(
            new HealthRecord.Code("http://nucc.org/provider-taxonomy", "208D00000X", "General Practice"),
            null));
    practitionerRole.addLocation()
        .setReference(findLocationUrl(clinician.getOrganization(), bundle))
        .setDisplay(clinician.getOrganization().name);
    if (clinician.getOrganization().phone != null
        && !clinician.getOrganization().phone.isEmpty()) {
      practitionerRole.addTelecom(new ContactPoint()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue(clinician.getOrganization().phone));
    }
    practitionerRole.addTelecom(practitionerResource.getTelecomFirstRep());

    newEntry(bundle, practitionerRole);
  }

  @Override
  public Goal careGoalExtension(Goal goalResource, Bundle bundle, Bundle.BundleEntryComponent personEntry, long carePlanStart, CodeableConcept goalStatus, JsonObject goal) {
    goalResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-goal\""));
    return goalResource;
  }

  @Override
  public void encounterExtensions(Encounter encounterResource,
                                  Bundle.BundleEntryComponent encounterComponent,
                                  HealthRecord.Encounter encounterModel,
                                  Patient patientResource,
                                  Bundle.BundleEntryComponent patientComponent,
                                  Person patientAgent,
                                  Bundle bundle) {
    String clinicalNoteText = ClinicalNoteExporter.export(patientAgent, encounterModel);
    boolean lastNote = encounterModel == patientAgent.record.encounters
        .get(patientAgent.record.encounters.size() - 1);
    createClinicalNote(patientComponent, patientResource, encounterComponent, encounterResource, clinicalNoteText, lastNote, bundle);
  }

  /**
   * Add a clinical note to the Bundle, which adds both a DocumentReference and a
   * DiagnosticReport.
   *
   * @param personComponent    The Entry for the Person
   * @param patientEntry       the FHIR Patient resource
   * @param encounterComponent the entry for the encounter in the bundle
   * @param encounterEntry     Current Encounter FHIR resource
   * @param clinicalNoteText   The plain text contents of the note.
   * @param currentNote        If this is the most current note.
   * @param bundle             Bundle to add the Report to
   */
  public void createClinicalNote(Bundle.BundleEntryComponent personComponent,
                                 Patient patientEntry,
                                 Bundle.BundleEntryComponent encounterComponent,
                                 Encounter encounterEntry,
                                 String clinicalNoteText,
                                 boolean currentNote,
                                 Bundle bundle) {
    // Add a DiagnosticReport
    DiagnosticReport reportResource = new DiagnosticReport();
    reportResource.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-note"));

    reportResource.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
    reportResource.addCategory(new CodeableConcept(
        new Coding(FhirR4.LOINC_URI, "34117-2", "History and physical note")));
    reportResource.getCategoryFirstRep().addCoding(
        new Coding(FhirR4.LOINC_URI, "51847-2", "Evaluation+Plan note"));
    reportResource.setCode(reportResource.getCategoryFirstRep());
    reportResource.setSubject(new Reference(personComponent.getFullUrl()));
    reportResource.setEncounter(new Reference(encounterComponent.getFullUrl()));
    reportResource.setEffective(encounterEntry.getPeriod().getStartElement());
    reportResource.setIssued(encounterEntry.getPeriod().getStart());
    if (encounterEntry.hasParticipant()) {
      reportResource.addPerformer(encounterEntry.getParticipantFirstRep().getIndividual());
    } else {
      reportResource.addPerformer(encounterEntry.getServiceProvider());
    }
    reportResource.addPresentedForm()
        .setContentType("text/plain")
        .setData(clinicalNoteText.getBytes());
    newEntry(bundle, reportResource);

    // Add a DocumentReference
    DocumentReference documentReference = new DocumentReference();
    documentReference.setMeta(FhirR4Specialisation.getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-documentreference"));

    if (currentNote) {
      documentReference.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
    } else {
      documentReference.setStatus(Enumerations.DocumentReferenceStatus.SUPERSEDED);
    }
    documentReference.addIdentifier()
        .setSystem("urn:ietf:rfc:3986")
        .setValue(reportResource.getId());
    documentReference.setType(reportResource.getCategoryFirstRep());
    documentReference.addCategory(new CodeableConcept(
        new Coding("http://hl7.org/fhir/us/core/CodeSystem/us-core-documentreference-category",
            "clinical-note", "Clinical Note")));
    documentReference.setSubject(new Reference(personComponent.getFullUrl()));
    documentReference.setDate(encounterEntry.getPeriod().getStart());
    documentReference.addAuthor(reportResource.getPerformerFirstRep());
    documentReference.setCustodian(encounterEntry.getServiceProvider());
    documentReference.addContent()
        .setAttachment(reportResource.getPresentedFormFirstRep())
        .setFormat(
            new Coding("http://ihe.net/fhir/ValueSet/IHE.FormatCode.codesystem",
                "urn:ihe:iti:xds:2017:mimeTypeSufficient", "mimeType Sufficient"));
    documentReference.setContext(new DocumentReference.DocumentReferenceContextComponent()
        .addEncounter(reportResource.getEncounter())
        .setPeriod(encounterEntry.getPeriod()));

    newEntry(bundle, documentReference);
  }

  @Override
  public void bundleExtensions(Bundle bundle,
                               Person person,
                               org.hl7.fhir.r4.model.Patient patientEntry,
                               long stopTime) {
    provenance(bundle, person, stopTime);
  }
}
