package org.mitre.synthea.export;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Extension for the FhirR4 exporter, producing resource bundles conforming to the
 * Core Dataset IG of the German MI-I (https://simplifier.net/medizininformatikinitiative-kerndatensatz)
 */
public class FhirR4DeKDSImplementationGuide implements FhirR4Specialisation {

  //region basic info

  @Override
  public Patient basicInfoExtension(Person person, //TODO
                                    Patient patientResource,
                                    long stopTime) {
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

  //region encounter

  @Override
  public Encounter encounterExtension(Person person, Encounter encounterResource, Patient patientResource, Bundle bundle, HealthRecord.Encounter encounter) { //TODO
    encounterResource = encounterForbidden(encounterResource);

    return encounterResource;
  }

  @Override
  public Encounter encounterForbidden(Encounter encounterResource) { //TODO
    return encounterResource;
  }

  //endregion

  //region condition

  @Override
  public Condition conditionExtension(Condition conditionResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Entry condition) {
    return conditionResource;
  }

  @Override
  public Condition conditionForbidden(Condition conditionResource) { //TODO
    return conditionResource;
  }

  //endregion

}
