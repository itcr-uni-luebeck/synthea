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

  //region OTHER_EXTENSIONS

  @Override
  public void bundleExtensions(Bundle bundle, Person person, Patient patientEntry, long stopTime) {
    //nyi
  }

  @Override
  public void encounterExtensions(Encounter encounterResource, Bundle.BundleEntryComponent encounterComponent, HealthRecord.Encounter encounterModel, Patient patientResource, Bundle.BundleEntryComponent patientComponent, Person patientAgent, Bundle bundle) {
    //nyi
  }

  //endregion
}
