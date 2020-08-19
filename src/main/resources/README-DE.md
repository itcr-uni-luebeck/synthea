# Einstellungen für MII-KDS

## synthea.properties

Die Konfigurationsoptionen für Synthea werden in `src/main/resources/synthea.properties` festgelegt. Eine Beispieldatei für die Verwendung mit deutschen Daten liegt unter `src/main/resources/synthea-de.properties`.
Hier sind die Einstellugen `exporter.baseDirectory`, `exporter.fhir.use_de_kds_ig` sowie `generate.demographics.default_file`, `generate.geography.*` und `generate.providers.*` besonders wichtig.

## deutsche Daten

ie deutschen Daten stehen unter https://github.com/itcr-uni-luebeck/synthea-international/tree/de-mii zur Verfügung. Diese müssen Synthea zur Verfügung gestellt werden:

- `src/main/resources/geography`: Die Dateien `demographics-de.csv`, `timezones-de.csv` und `zipcodes-de.csv` müssen in den Pfad kopiert werden und die Optionen `generate.demographics.default_file` und `generate.geography.zipcodes/timezones.default_file` angepasst werden. 
- `src/main/resources/payers`: Da die Abbildung des deutschen Versicherungsmodells schwierig ist (einkommensabhängige Prämien, sehr geringe Deductibles), haben wir bislang eine Versicherung `National Health Service` verwendet, die gar nichts kostet. Diese ist in `insurance_companies-de.csv` definiert. Die Option ist `generate.payers.insurance_companies.default_file`
- `src/main/resources/providers`: Hier werden die Leistungserbringer definiert. Obwohl in Synthea Dateien für viele verschiedene Typen von Leistungserbringern liegen, werden nur folgende 4 Dateien aktuell verwendet: `hospitals`, `veterans`, `primarycare`, and `urgentcare`. Diese 4 Dateien haben wir für die MII-Häuser angepasst, die Optionen dafür sind also `generate.providers.*.default_file`.
- `src/main/resources/names.yml`: Diese Datei definiert die Namen, die die Patienten bekommen. Diese Datei muss ersetzt werden, es gibt keine Konfigurationsoption. Die Datei muss drei Abschnitte haben: `english`, `spanish` und  `street`. `english` und `spanish` haben jeweils die Abschnitte `family`, `M` und `F`, währed `street` die Abschnitte `type` und `secondary` hat. Die Abschnitte `english` und `spanish` sind identisch!