package org.mitre.synthea.export;

import org.hl7.fhir.r4.model.*;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Extension for the FhirR4 exporter, producing resource bundles conforming to the
 * Core Dataset IG of the German MI-I (https://simplifier.net/medizininformatikinitiative-kerndatensatz)
 */
public class FhirR4DeKDSImplementationGuide implements FhirR4Specialisation {

  //region BASIC_INFO
  @Override
  public Patient basicInfoExtension(Patient patientResource, Person person, //TODO
                                    long stopTime) {
    patientResource = basicInfoForbidden(patientResource);
    return patientResource;
  }

  @Override
  public Patient basicInfoForbidden(Patient patientResource) { //TODO
    Stream<String> allowedIdentifiers = Arrays.stream(new String[]{
        "http://hospital.smarthealthit.org"});
    removeInvalidIdentifiersBasicInfo(patientResource, allowedIdentifiers);

    return patientResource;
  }
  //endregion

  //region ENCOUNTER
  @Override
  public Encounter encounterExtension(Encounter encounterResource, Person person, Patient patientResource, Bundle bundle, HealthRecord.Encounter encounter) { //TODO
    encounterResource = encounterForbidden(encounterResource);

    return encounterResource;
  }

  @Override
  public Encounter encounterForbidden(Encounter encounterResource) { //TODO
    return encounterResource;
  }
  //endregion

  //region CONDITION
  @Override
  public Condition conditionExtension(Condition conditionResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry condition) {
    return conditionResource;
  }

  @Override
  public Condition conditionForbidden(Condition conditionResource) { //TODO
    return conditionResource;
  }
  //endregion

  //region ALLERGY
  @Override
  public AllergyIntolerance allergyExtension(AllergyIntolerance allergyResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry allergy) { //DONE ?
    allergyResource = allergyForbidden(allergyResource);
    return allergyResource; //allergy not specified in MII KDS, AFAIK [JW]
  }
  //endregion

  //region OBSERVATION
  @Override
  public Observation observationExtension(Observation observationResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Observation observation) { //TODO
    return observationResource;
  }

  @Override
  public Observation observationForbidden(Observation observationResource) { //TODO
    return observationResource;
  }
  //endregion

  //region PROCEDURE
  @Override
  public Procedure procedureExtension(Procedure procedureResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Procedure procedure) {
    return null;
  }
  //endregion


  //region DEVICE
  @Override
  public Device deviceExtension(Device deviceResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, HealthRecord.Device device) { //TODO
    return deviceResource;
  }
  //endregion
}
