package org.mitre.synthea.export;

import org.hl7.fhir.r4.model.*;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extension for generating FHIR resource bundles conforming to
 * (national) Implementation Guides, such as the US Core IG or the
 * German Medical Informatics Initiative (MI-I) Core Dataset IG
 */
public interface FhirR4Specialisation {

  /**
   * generate a Meta instance that claims conformance to the given profile definition
   *
   * @param profileURI the URI of the StructureDefinition this resource claims to conform to
   * @return a Meta instance with the given profileURI
   */
  default Meta getConformanceToProfileMeta(String profileURI) {
    return new Meta().addProfile(profileURI);
  }

  //region BASIC INFO

  /**
   * Extension to FhirR4 for adding basic info required by an IG
   *
   * @param person          the person agent to render
   * @param patientResource the patient that was already rendered
   * @param stopTime        Time the simulation ended
   * @return the created patient entry
   */
  Patient basicInfoExtension(Person person,
                             Patient patientResource,
                             long stopTime);

  /**
   * Remove attributes/resources that are forbidden by the IG
   *
   * @param patientResource the resource to remove from
   * @return the cleaned-up patient resource
   * @implNote passes the encounter through if nothing is forbidden for this IG
   */
  default Patient basicInfoForbidden(Patient patientResource) {
    return patientResource; //pass through if nothing is forbidden
  }

  /**
   * remove all identifiers that do not feature a System in the whitelist.
   * Example use: Drivers Licence is not used in Europe for Patient Identification
   *
   * @param patientResource          the resource to modify
   * @param allowedIdentifierSystems the whitelist that allows identifiers for the IG
   */
  default void removeInvalidIdentifiersBasicInfo(Patient patientResource, Stream<String> allowedIdentifierSystems) {
    List<Identifier> identifier = patientResource
        .getIdentifier()
        .stream()
        .filter(i -> allowedIdentifierSystems.anyMatch(i::equals))
        .collect(Collectors.toList());
    patientResource.setIdentifier(identifier);
  }

  //endregion

  //region ENCOUNTER

  /**
   * add specific elements to the encounter
   *
   * @param person            the person to render
   * @param encounterResource the already-rendered encounter resource
   * @param patientResource   the patient resource this encounter refers to
   * @param bundle            the genreated bundle to add to
   * @param encounter         the encounter from Synthea's model
   * @return the modified encounter
   */
  Encounter encounterExtension(Person person,
                               Encounter encounterResource,
                               Patient patientResource,
                               Bundle bundle,
                               HealthRecord.Encounter encounter);

  /**
   * Remove attributes/resources that are forbidden by the IG in the encounter resource
   *
   * @param encounterResource the encounter resource that is rendered
   * @return the modified encounter resource
   * @implNote passes the encounter through if nothing is forbidden for this IG
   */
  default Encounter encounterForbidden(Encounter encounterResource) {
    return encounterResource; //pass through if nothing is forbidden
  }

  //endregion

  //region CONDITION

  /**
   * add specific elements to the encounter
   *
   * @param conditionResource
   * @param personEntry
   * @param bundle
   * @param encounterEntry
   * @param condition
   * @return
   */
  Condition conditionExtension(
      Condition conditionResource,
      Bundle.BundleEntryComponent personEntry,
      Bundle bundle,
      Bundle.BundleEntryComponent encounterEntry,
      HealthRecord.Entry condition);

  /**
   * remove forbidden elements from the condition resource
   *
   * @param conditionResource the resource to clean
   * @return the cleaned resource
   * @implNote default implementation passes the condition through unmodified, i.e. everything is allowed
   */
  default Condition conditionForbidden(Condition conditionResource) {
    return conditionResource;
  }

  //endregion
}
