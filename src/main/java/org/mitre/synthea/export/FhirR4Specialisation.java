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

  //region UTILS
  /**
   * generate a Meta instance that claims conformance to the given profile definition
   *
   * @param profileURI the URI of the StructureDefinition this resource claims to conform to
   * @return a Meta instance with the given profileURI
   * @implNote Usage: resource.setMeta(getConformanceToProfileMeta("http://hl7.org/fhir/us/core/StructureDefinition/us-core-allergyintolerance"))
   */
  default Meta getConformanceToProfileMeta(String profileURI) {
    return new Meta().addProfile(profileURI);
  }
  //endregion

  //region BASIC INFO
  /**
   * Extension to FhirR4 for adding basic info required by an IG
   *
   * @param patientResource the patient that was already rendered
   * @param person          the person agent to render
   * @param stopTime        Time the simulation ended
   * @return the created patient entry
   */
  Patient basicInfoExtension(Patient patientResource, Person person, long stopTime);

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
        .filter(i -> allowedIdentifierSystems.anyMatch(ai -> i.getSystem().equals(ai)))
        .collect(Collectors.toList());
    patientResource.setIdentifier(identifier);
  }
  //endregion

  //region ENCOUNTER
  /**
   * add specific elements to the encounter
   *
   * @param encounterResource the already-rendered encounter resource
   * @param person            the person to render
   * @param patientResource   the patient resource this encounter refers to
   * @param bundle            the genreated bundle to add to
   * @param encounter         the encounter from Synthea's model
   * @return the modified encounter
   */
  Encounter encounterExtension(Encounter encounterResource,
                               Person person,
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
   * @param conditionResource the condition resource to modify
   * @param personEntry       the person that is being referenced
   * @param bundle            the bundle that is being generated
   * @param encounterEntry    the encounter that is being referred to
   * @param condition         the condition of the subject
   * @return the modified conditionResource
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

  //region ALLERGY
  /**
   * IG-specific extensions for AllergyIntolerance
   *
   * @param allergyResource the resource being modified
   * @param personEntry     the subject of the allergyResource
   * @param bundle          the bundle being added to
   * @param encounterEntry  the encounter this allergy belongs to
   * @param allergy         the allergy that is being encoded
   * @return the modified allergyResource
   */
  AllergyIntolerance allergyExtension(AllergyIntolerance allergyResource,
                                      Bundle.BundleEntryComponent personEntry,
                                      Bundle bundle,
                                      Bundle.BundleEntryComponent encounterEntry,
                                      HealthRecord.Entry allergy);

  /**
   * remove forbidden elements from the AllergyIntolerance resource
   *
   * @param allergyIntolerance the resource in question
   * @return the modified resource
   */
  default AllergyIntolerance allergyForbidden(AllergyIntolerance allergyIntolerance) {
    return allergyIntolerance;
  }
  //endregion

  //region OBSERVATION
  Observation observationExtension(
      Observation observationResource,
      Bundle.BundleEntryComponent personEntry,
      Bundle bundle,
      Bundle.BundleEntryComponent encounterEntry,
      HealthRecord.Observation observation);

  default Observation observationForbidden(Observation observationResource) {
    return observationResource;
  }
  //endregion

  //region PROCEDURE
  /**
   * IG-specific extensions for the Procedure resource
   *
   * @param procedureResource the procedure resource being modified
   * @param personEntry       the subject of the procedure
   * @param bundle            the bundle to add to
   * @param encounterEntry    the encounter being referenced
   * @param procedure         the procedure that is being rendered
   * @return the modified procedure
   */
  Procedure procedureExtension(Procedure procedureResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, Bundle.BundleEntryComponent encounterEntry, HealthRecord.Procedure procedure);

  /**
   * remove forbidden elements from the procedure resource
   *
   * @param procedureResource the resource to modify
   * @return the modified resource
   */
  default Procedure procedureForbidden(Procedure procedureResource) {
    return procedureResource;
  }
  //endregion

  //region DEVICE
  /**
   * IG-specific extensions for the device resource
   *
   * @param deviceResource the device resource being modified
   * @param personEntry the person that is being referenced
   * @param bundle the bundle being added to
   * @param device the device being rendered
   * @return the modified resource
   */
  Device deviceExtension(Device deviceResource, Bundle.BundleEntryComponent personEntry, Bundle bundle, HealthRecord.Device device);

  /**
   * remove forbidden data from the device resources
   * @param deviceResource the resource being modified
   * @return the resource
   */
  default Device deviceForbidden(Device deviceResource) {
    return deviceResource;
  }
  //endregion
}
