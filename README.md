# ESRS Set1 XBRL Taxonomy - Java 25 Pipeline

Dieses Repository enthaelt:

- das lokale EFRAG ESRS Set-1 Taxonomiepaket,
- eine Java-25/Maven-Referenzpipeline zur Erzeugung von XBRL und iXBRL,
- Arelle-basierte Validierung,
- iXBRL-Viewer-Export fuer eine interaktive HTML-Sicht,
- Dokumentation und Checklisten unter `md-files/`.

## Ziele des Projekts

Die Pipeline erzeugt aus strukturierten Eingabedaten:

1. `output/report-instance.xml` (XBRL Instanz),
2. `output/report-ixbrl.xhtml` (iXBRL Bericht),
3. `output/report-interaktiv.html` (Viewer-Ausgabe),
4. `output/arelle-xbrl.log` und `output/arelle-ixbrl.log` (Validierungslogs).

## Voraussetzungen

Empfohlen auf Windows:

- Java 25
- Maven 3.9+
- PowerShell 7+ oder Windows PowerShell
- Arelle CLI (`arelleCmdLine`) im PATH

Optional/empfohlen fuer strikte Produktionstests:

- iXBRL Viewer Plugin fuer Arelle
- Python-Paket `bottle` (wird vom Viewer-Plugin benoetigt)

Beispiel:

```powershell
python -m pip install --user bottle
```

## Schnellstart

Im Projektordner:

```powershell
mvn test
```

Standardlauf (ohne Arelle-Blockade):

```powershell
$env:SKIP_ARELLE = "true"
mvn exec:java
```

## Strict Production Gate

Fuer einen harten End-to-End-Gate mit Tests, Arelle-Validierung und Viewer-Pflicht:

```powershell
./scripts/run-strict-production-gate.ps1 -ArelleCmd "arelleCmdLine"
```

Dieses Skript setzt:

- `SKIP_ARELLE=false`
- `FAIL_ON_VALIDATION_ISSUES=true`
- `REQUIRE_VIEWER_PLUGIN=true`

und beendet den Lauf mit Fehlercode ungleich 0 bei jedem Gate-Fehler.

## Wichtige Umgebungsvariablen

- `ARELLE_CMD`: Kommando/Pfad zur Arelle CLI
- `SKIP_ARELLE`: `true|false`
- `FAIL_ON_VALIDATION_ISSUES`: `true|false`
- `REQUIRE_VIEWER_PLUGIN`: `true|false`
- `IXBRL_VIEWER_PLUGIN`: Optionaler expliziter Plugin-Pfad
- `ARELLE_LOG_FORMAT`: Optionales Arelle-Logformat

## Daten, Mapping, Templates

Zentrale Projektdateien:

- `src/main/resources/testdata/fictive-esrs-input.json`
- `mapping/map-esrs-2023-12-22.json`
- `mapping/report-layout-map.json`
- `templates/report-base.xhtml`
- `templates/assets/report.css`
- `templates/assets/report.js`

Hinweise:

- Das Mapping steuert Konzept, Typ, Zeitraum, Einheit und Dimensionen.
- Das Template liefert die XHTML/iXBRL-Basisstruktur.
- Das Layout-Mapping verknuepft Template-Placeholder mit Eingabefeldern.

## Build- und Testbefehle

```powershell
mvn clean
mvn compile
mvn test
mvn exec:java
mvn -Pcoverage verify
```

Einzeltests:

```powershell
mvn -Dtest=FactBuilderTest test
mvn -Dtest=FactBuilderTest#shouldRejectInvalidNumericValue test
```

## Projektstruktur (Kurz)

- `src/main/java/org/esrs/pipeline/`: Pipeline-Code
- `src/test/java/org/esrs/pipeline/`: Unit-/Integrationstests
- `mapping/`: Feld-zu-Konzept-Mapping und Layout-Mapping
- `templates/`: XHTML-Template und Assets
- `xbrl.efrag.org/`: lokale ESRS-Taxonomie
- `META-INF/`: Taxonomy package Metadaten
- `scripts/`: Hilfsskripte
- `md-files/`: fachliche und technische Dokumentation

## Dokumentation

Alle Projektleitdokumente liegen in `md-files/`, insbesondere:

- `md-files/IMPLEMENTIERUNGSLEITFADEN_JAVA_XBRL_ESRS.md`
- `md-files/CHECKLISTE_STATUS_XBRL_IXBRL_JAVA.md`
- `md-files/ANLEITUNG_START_MAVEN_TESTS_TEMPLATE.md`
- `md-files/TECHNISCHE_GRUNDLAGEN.md`
- `md-files/PROJEKTSTRUKTUR.md`
- `md-files/GLOSSAR_XBRL_ESRS.md`
- `md-files/KI_INSTRUKTIONEN_XBRL_JAVA25.md`

## Bekannte Betriebsregeln

- In Strict-Mode ist Viewer-Fallback nicht erlaubt.
- Arelle-Logs werden pro Lauf ueberschrieben (deterministische Fehleranalyse).
- Die ESRS-Namespace-Verwendung ist auf die Taxonomie abgestimmt.
- Bei Mapping-Erweiterungen immer Tests und Strict-Gate erneut ausfuehren.

## Typischer Entwicklungsablauf

1. Mapping/Testdaten/Template anpassen
2. `mvn test`
3. `./scripts/run-strict-production-gate.ps1 -ArelleCmd "arelleCmdLine"`
4. Ergebnisse in `output/` pruefen
5. Aenderungen committen/taggen

## Lizenz- und Quellenhinweis

Die Taxonomieinhalte stammen aus dem EFRAG ESRS Taxonomy Package. Details siehe Metadaten in `META-INF/taxonomyPackage.xml` und Taxonomiebaum unter `xbrl.efrag.org/`.
