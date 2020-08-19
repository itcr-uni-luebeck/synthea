package org.mitre.synthea.editors;

import org.mitre.synthea.engine.HealthRecordEditor;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InpatientMovingEditor implements HealthRecordEditor {

  /**
   * this editor only handles inpatient encounters
   * @param person The Synthea person to check on whether the module should be run
   * @param record The person's HealthRecord
   * @param time The current time in the simulation
   * @return true iff at least one inpatient encounter exists
   */
  @Override
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    return getInpatientEncountersWithoutMovement(record.encounters).findAny().isPresent();
  }

  @Override
  public void process(Person person, List<HealthRecord.Encounter> encounters, long time, Random random) {
    List<HealthRecord.Encounter> inpatientEncounters = getInpatientEncountersWithoutMovement(encounters).collect(Collectors.toList());
    List<HealthRecord.EncounterWithMovement> enrichedEncounters = inpatientEncounters
        .stream()
        .map((HealthRecord.Encounter baseEncounter) -> enrichEncounter(baseEncounter, person.record, random))
        .collect(Collectors.toList());
    encounters.removeAll(inpatientEncounters);
    encounters.addAll(enrichedEncounters);
  }

  public HealthRecord.EncounterWithMovement enrichEncounter(HealthRecord.Encounter baseEncounter, HealthRecord record, Random random) {
    HealthRecord.EncounterWithMovement withMovement = record.new EncounterWithMovement(baseEncounter);
    //every inpatient encounter will have had an admission
    withMovement.addMovement(HealthRecord.MovementType.ADMISSION);

    //the admisission is followed by a transfer to a bed in the same department
    withMovement.addMovement(HealthRecord.MovementType.INPATIENT);
    return withMovement;
  }

  private Stream<HealthRecord.Encounter> getInpatientEncountersWithoutMovement(List<HealthRecord.Encounter> encounters) {
    return encounters.stream()
        .filter(e -> e.type.equalsIgnoreCase(HealthRecord.EncounterType.INPATIENT.toString()))
        .filter(e -> !(e instanceof HealthRecord.EncounterWithMovement));
  }
}
