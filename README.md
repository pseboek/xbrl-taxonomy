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
4. `output/taxonomy-visualization.html` (Visualisierungs-Index),
5. `output/taxonomy-visualization-tree.html` (Tree View),
6. `output/taxonomy-visualization-graph.html` (Graph View),
7. `output/taxonomy-visualization-layer.html` (Layer View),
8. `output/taxonomy-visualization-matrix.html` (Matrix View),
9. `output/taxonomy-visualization-flow.html` (Flow View),
10. `output/taxonomy-visualization-hypercube.html` (Hypercube View),
11. `output/taxonomy-visualization-coverage.html` (Coverage View),
12. `output/taxonomy-visualization-enumeration.html` (Enumeration View),
13. `output/taxonomy-visualization-reference.html` (Reference View),
14. `output/taxonomy-visualization-calculation.html` (Calculation View),
15. `output/taxonomy-visualization-intersection.html` (Intersection View),
16. `output/taxonomy-visualization-validation.html` (Validation View),
17. `output/taxonomy-visualization-allocation.html` (Allocation View),
18. `output/arelle-xbrl.log` und `output/arelle-ixbrl.log` (Validierungslogs).

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

## Visualisierungen erzeugen

Die Taxonomie-Visualisierungen werden bei einem normalen Pipeline-Lauf automatisch geschrieben. Fuer die lokale Erzeugung reicht meist:

```powershell
$env:SKIP_ARELLE = "true"
mvn exec:java
```

Danach findest du die Dateien unter `output/`:

- `taxonomy-visualization.html` als Startseite/Index
- `taxonomy-visualization-tree.html` fuer Hierarchie + Drilldown
- `taxonomy-visualization-graph.html` fuer den interaktiven Dependency-Graph
- `taxonomy-visualization-layer.html` fuer Layer-Analyse mit aufklappbaren Unterelementen
- `taxonomy-visualization-matrix.html` fuer Konzept- und Mapping-Analyse
- `taxonomy-visualization-flow.html` fuer die Prozess-/Journey-Sicht
- `taxonomy-visualization-hypercube.html` fuer dimensionale Analyse (Hypercubes, Achsen, Domains, Member)
- `taxonomy-visualization-coverage.html` fuer Abdeckungsanalyse (Mapping/Layout/Enumeration/Dimensionen)
- `taxonomy-visualization-enumeration.html` fuer Enumeration-Domaenen und Allowed Values
- `taxonomy-visualization-reference.html` fuer Konzept-zu-ESRS-Referenzen (Traceability)
- `taxonomy-visualization-calculation.html` fuer Calculation- und Formula-Dependency-Analyse
- `taxonomy-visualization-intersection.html` fuer Dimensionspaar-Kombinationen je Hypercube
- `taxonomy-visualization-validation.html` fuer Rule-Dependency-Analyse (Formula-Dateien -> Konzepte)
- `taxonomy-visualization-allocation.html` fuer Section-zu-Placeholder-zu-Konzept-Zuordnung

Der Explorer enthaelt 13 Ansichten:

1. Tree: Presentation-Hierarchie mit Drilldown
2. Graph: Interaktiver Abhaengigkeitsgraph (Sample) aus Linkbase-Kanten
3. Layer: Linkbase-Layer (Dateien/Kanten) + externe HREF-Samples
4. Matrix: Konzeptindex + Layout-Zuordnung
5. Flow: Reporting-Flow von Datensammlung bis Disclosure
6. Hypercube: Dimensionale Struktur (all/notAll, Dimensionen, Domains, Member)
7. Coverage: Abdeckungsanalyse je Konzept (Mapping/Layout/Enumeration/Dimensionen)
8. Enumeration: Browser fuer Domains, Allowed Values und Taxonomie-Infos (enum2:item/set)
9. Reference: Traceability von Konzepten zu ESRS-/Regulations-Referenzen
10. Calculation: Impact-Analyse fuer Calculation-Kanten und Formula-Konzeptverwendungen
11. Intersection: Dimensionspaar-Analyse pro Hypercube (A x B Kombinationen)
12. Validation: Regelabhaengigkeiten aus Formula-Dateien und Konzept-Mentions
13. Allocation: Template-/Placeholder-Zuordnung je Section mit Konzeptbezug

## Visualisierungen lesen und verstehen

Die ausfuehrliche Leseanleitung fuer Tree, Graph, Layer, Matrix, Flow und Hypercube liegt in:

- `md-files/README_VISUALISIERUNGEN.md`

Die Doku enthaelt auch Beispiele und Mermaid-Diagramme zur Hypercube-Interpretation.

Wenn du den vollen End-to-End-Lauf mit Tests und Validierung willst, nutze alternativ:

```powershell
./scripts/run-strict-production-gate.ps1 -ArelleCmd "arelleCmdLine"
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
- `output/taxonomy-visualization.html` — Indexseite mit Links auf getrennte Visualisierungsansichten.
- `output/taxonomy-visualization-tree.html` — Baumansicht aus der Presentation-Linkbase.
- `output/taxonomy-visualization-graph.html` — interaktive Graphansicht (Layer-Toggles, Zoom/Pan, Suche/Fokus, Themenfarben, Nachbarschafts-Highlight).
- `output/taxonomy-visualization-layer.html` — Layer-Übersicht mit aufklappbaren Unterelementen.
- `output/taxonomy-visualization-matrix.html` — analytische Matrixsicht für Konzepte und Mapping.
- `output/taxonomy-visualization-flow.html` — Prozesssicht entlang des Reporting-Flows.
- `output/taxonomy-visualization-hypercube.html` — Dimensionensicht mit Hypercubes, Achsen, Domains, Default-Members und Domain-Members.
- `output/taxonomy-visualization-coverage.html` — Abdeckungssicht je Konzept (Layout/Enumeration/Dimensionen).
- `output/taxonomy-visualization-enumeration.html` — Enumeration-Sicht mit Domain-/Allowed-Value-Informationen.
- `output/taxonomy-visualization-reference.html` — Referenzsicht fuer Konzept-zu-Norm-Traceability.
- `output/taxonomy-visualization-calculation.html` — Dependency-Sicht fuer Calculation-Kanten und Formula-Mentions.
- `output/taxonomy-visualization-intersection.html` — Dimensionspaar-Sicht mit Kombinationen pro Hypercube.
- `output/taxonomy-visualization-validation.html` — Validation-Rule-Sicht mit Formula-Dateien und referenzierten Konzepten.
- `output/taxonomy-visualization-allocation.html` — Zuordnungssicht Section -> Placeholder -> Feld -> Konzept.

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

- `md-files/README_VISUALISIERUNGEN.md`
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
