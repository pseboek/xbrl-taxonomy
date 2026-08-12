# ESRS Set1 XBRL Taxonomy - Java 25 Pipeline

Dieses Repository enthält:

- das lokale EFRAG ESRS Set-1 Taxonomiepaket,
- eine Java-25/Maven-Referenzpipeline zur Erzeugung von XBRL und iXBRL,
- Arelle-basierte Validierung,
- einen iXBRL-Viewer-Export für eine interaktive HTML-Sicht,
- eine aktuelle Taxonomie-Visualisierungs-Suite mit mehreren HTML-Ansichten,
- Dokumentation und Checklisten unter `md-files/`.

## Aktueller Projektstand

Der aktuelle Output im Repository enthält bereits die generierten Artefakte unter `output/`, darunter:

- `report-instance.xml`
- `report-ixbrl.xhtml`
- `report-interaktiv.html`
- `taxonomy-visualization.html` als zentrale Startseite
- die einzelnen Visualisierungsansichten wie `taxonomy-visualization-tree.html`, `graph`, `layer`, `matrix`, `flow`, `hypercube`, `dashboard` und weitere Analyseviews.

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
11. `output/taxonomy-visualization-hypercube-3d.html` (Hypercube 3D View),
12. `output/taxonomy-visualization-coverage.html` (Coverage View),
13. `output/taxonomy-visualization-enumeration.html` (Enumeration View),
14. `output/taxonomy-visualization-reference.html` (Reference View),
15. `output/taxonomy-visualization-calculation.html` (Calculation View),
16. `output/taxonomy-visualization-intersection.html` (Intersection View),
17. `output/taxonomy-visualization-validation.html` (Validation View),
18. `output/taxonomy-visualization-allocation.html` (Allocation View),
19. `output/taxonomy-visualization-stats.html` (Stats View),
20. `output/taxonomy-visualization-complexity.html` (Complexity View),
21. `output/taxonomy-visualization-impact-heatmap.html` (Impact Heatmap View),
22. `output/taxonomy-visualization-hypercube-dimension-inventory.html` (Hypercube Dimension Inventar),
23. `output/taxonomy-visualization-mapping-flow.html` (Mapping Flow View),
24. `output/taxonomy-visualization-concept-backlog.html` (Concept Backlog View),
25. `output/taxonomy-visualization-scope-period-analysis.html` (Scope & Period Analysis),
26. `output/taxonomy-visualization-rule-coverage-matrix.html` (Rule Coverage Matrix),
27. `output/taxonomy-visualization-intersection-risk.html` (Intersection Risk View),
28. `output/taxonomy-visualization-traceability-matrix.html` (Traceability Matrix View),
29. `output/taxonomy-visualization-dimension-cooccurrence.html` (Dimension Co-Occurrence View),
30. `output/taxonomy-visualization-default-member-quality.html` (Default Member Quality View),
31. `output/taxonomy-visualization-enum-domain-validity.html` (Enum Domain Validity View),
32. `output/taxonomy-visualization-dashboard.html` (Master Dashboard),
33. `output/arelle-xbrl.log` und `output/arelle-ixbrl.log` (Validierungslogs).

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

Standardlauf ohne Arelle-Blockade:

```powershell
$env:SKIP_ARELLE = "true"
mvn exec:java
```

Alternativ direkt mit der aktuellen Projektkonfiguration:

```powershell
mvn -B exec:java
```

> Wichtig: Der Pipeline-Lauf erzeugt die HTML-Visualisierungen automatisch unter `output/`. Wenn Arelle lokal verfügbar ist, kann der volle Validierungslauf mit dem Strict-Gate gestartet werden.

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
- `taxonomy-visualization-hypercube-3d.html` fuer interaktive 3D-Navigation durch Hypercubes und Dimensionen
- `taxonomy-visualization-coverage.html` fuer Abdeckungsanalyse (Mapping/Layout/Enumeration/Dimensionen)
- `taxonomy-visualization-enumeration.html` fuer Enumeration-Domaenen und Allowed Values
- `taxonomy-visualization-reference.html` fuer Konzept-zu-ESRS-Referenzen (Traceability)
- `taxonomy-visualization-calculation.html` fuer Calculation- und Formula-Dependency-Analyse
- `taxonomy-visualization-intersection.html` fuer Dimensionspaar-Kombinationen je Hypercube
- `taxonomy-visualization-validation.html` fuer Rule-Dependency-Analyse (Formula-Dateien -> Konzepte)
- `taxonomy-visualization-allocation.html` fuer Section-zu-Placeholder-zu-Konzept-Zuordnung
- `taxonomy-visualization-stats.html` fuer Linkbase-Edge-Statistik und Struktur-Hinweise
- `taxonomy-visualization-complexity.html` fuer gewichtete Komplexitaetsanalyse je Konzept
- `taxonomy-visualization-impact-heatmap.html` fuer Konzept-x-Section-Impactanalyse mit filterbarer Heatmap-Tabelle
- `taxonomy-visualization-hypercube-dimension-inventory.html` fuer filterbare Inventarsicht je Hypercube-Achse (Domains, Members, Defaults)
- `taxonomy-visualization-mapping-flow.html` fuer Feld-zu-Konzept-zu-Hypercube-Flowanalyse mit Filtern
- `taxonomy-visualization-concept-backlog.html` fuer priorisierte Konzept-Backlog-Analyse
- `taxonomy-visualization-scope-period-analysis.html` fuer Sicht auf Section/Periode/Einheit
- `taxonomy-visualization-rule-coverage-matrix.html` fuer Formula-Datei-zu-Konzept-Abdeckung
- `taxonomy-visualization-intersection-risk.html` fuer Risiko-Ranking von Dimensionspaaren
- `taxonomy-visualization-traceability-matrix.html` fuer Referenz/Feld/Placeholder-Traceability
- `taxonomy-visualization-dimension-cooccurrence.html` fuer Analyse haeufiger Dimensionspaarungen
- `taxonomy-visualization-default-member-quality.html` fuer Default-Member-Qualitaetschecks
- `taxonomy-visualization-enum-domain-validity.html` fuer Domain- und Allowed-Value-Uebersicht
- `taxonomy-visualization-dashboard.html` als zentraler Einstieg mit globaler Suche/Filterung

Der aktuelle Explorer liefert derzeit 29 HTML-Ansichten plus die zentrale Startseite:

1. Tree: Presentation-Hierarchie mit Drilldown
2. Graph: Interaktiver Abhaengigkeitsgraph (Sample) aus Linkbase-Kanten
3. Layer: Linkbase-Layer (Dateien/Kanten) + externe HREF-Samples
4. Matrix: Konzeptindex + Layout-Zuordnung
5. Flow: Reporting-Flow von Datensammlung bis Disclosure
6. Hypercube: Dimensionale Struktur (all/notAll, Dimensionen, Domains, Member)
7. Hypercube 3D: Raeumliche Navigation mit Zoom/Orbit und Interaktionsdetails
8. Coverage: Abdeckungsanalyse je Konzept (Mapping/Layout/Enumeration/Dimensionen)
9. Enumeration: Browser fuer Domains, Allowed Values und Taxonomie-Infos (enum2:item/set)
10. Reference: Traceability von Konzepten zu ESRS-/Regulations-Referenzen
11. Calculation: Impact-Analyse fuer Calculation-Kanten und Formula-Konzeptverwendungen
12. Intersection: Dimensionspaar-Analyse pro Hypercube (A x B Kombinationen)
13. Validation: Regelabhaengigkeiten aus Formula-Dateien und Konzept-Mentions
14. Allocation: Template-/Placeholder-Zuordnung je Section mit Konzeptbezug
15. Stats: Struktur- und Qualitaetssicht auf Linkbase-Kanten, Top-Knoten und Randknoten
16. Complexity: Scoring fuer Konzeptkomplexitaet (Dimensionen, Enumeration, Calculation, Formula)
17. Impact Heatmap: Priorisierung von Konzept-Section-Paaren nach Impact-Score (Mappings, Dimensionen, Enumeration, Placeholder)
18. Hypercube Dimension Inventar: Filterbare Achseninventarliste fuer Hypercubes mit Domain/Member/Default-Kennzahlen
19. Mapping Flow: Sankey-orientierte Flows Feld -> Konzept -> Hypercube als filterbare Tabelle
20. Concept Backlog: Priorisierte Konzeptliste nach Risiko- und Abdeckungsindikatoren
21. Scope & Period: Analyse je Section, Periode und Einheit mit Mapping-Signalen
22. Rule Coverage Matrix: Formula-Datei x Konzept mit Mapping-Abdeckung
23. Intersection Risk: Risiko-Ranking fuer dimensionsbasierte Kombinationsraume
24. Traceability Matrix: Konzept-zu-Referenz-zu-Feld-Zuordnung in Tabellenform
25. Dimension Co-Occurrence: Haeufigkeitsanalyse von Dimensionspaaren ueber Hypercubes
26. Default Member Quality: Status je Dimension fuer fehlende/mehrfache Defaults
27. Enum Domain Validity: Enumeration-Domain-Konsistenz und Value-Transparenz
28. Master Dashboard: Zentrale Hub-Seite mit globaler Suche und Themenfiltern
29. Indexseite: zentrale Landingpage mit Verlinkung aller Analyseansichten

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
- `output/taxonomy-visualization-hypercube-3d.html` — interaktive 3D-Dimensionensicht mit voller Stage-Breite, Orbit und Fokus auf Cube/Dimension.
- `output/taxonomy-visualization-coverage.html` — Abdeckungssicht je Konzept (Layout/Enumeration/Dimensionen).
- `output/taxonomy-visualization-enumeration.html` — Enumeration-Sicht mit Domain-/Allowed-Value-Informationen.
- `output/taxonomy-visualization-reference.html` — Referenzsicht fuer Konzept-zu-Norm-Traceability.
- `output/taxonomy-visualization-calculation.html` — Dependency-Sicht fuer Calculation-Kanten und Formula-Mentions.
- `output/taxonomy-visualization-intersection.html` — Dimensionspaar-Sicht mit Kombinationen pro Hypercube.
- `output/taxonomy-visualization-validation.html` — Validation-Rule-Sicht mit Formula-Dateien und referenzierten Konzepten.
- `output/taxonomy-visualization-allocation.html` — Zuordnungssicht Section -> Placeholder -> Feld -> Konzept.
- `output/taxonomy-visualization-stats.html` — Statistiksicht auf Layer, Kantenanteile und Knotengrade.
- `output/taxonomy-visualization-complexity.html` — Komplexitaetssicht mit Risiko-Score je Konzept.
- `output/taxonomy-visualization-impact-heatmap.html` — Heatmap-Sicht fuer Konzept-Section-Impact mit Filterung nach Section und Mindestscore.
- `output/taxonomy-visualization-hypercube-dimension-inventory.html` — Inventarsicht pro Hypercube-Dimension mit Filtern fuer Members, Defaults und typed-axis-Indikator.
- `output/taxonomy-visualization-mapping-flow.html` — Flow-Sicht fuer Feld-zu-Konzept-zu-Hypercube-Pfade mit Section- und Dimensionsfiltern.
- `output/taxonomy-visualization-concept-backlog.html` — priorisierte Backlog-Sicht je Konzept mit Risikoindikatoren.
- `output/taxonomy-visualization-scope-period-analysis.html` — Analyse von Periode und Einheit je Reporting-Section.
- `output/taxonomy-visualization-rule-coverage-matrix.html` — Matrixsicht Formula-Dateien zu referenzierten Konzepten.
- `output/taxonomy-visualization-intersection-risk.html` — Risikoanalyse fuer Dimensionspaar-Kombinationen.
- `output/taxonomy-visualization-traceability-matrix.html` — Matrix fuer Referenzen, Felder und Placeholders je Konzept.
- `output/taxonomy-visualization-dimension-cooccurrence.html` — Sicht auf haeufig gemeinsam auftretende Dimensionen.
- `output/taxonomy-visualization-default-member-quality.html` — Qualitaetsansicht fuer Default-Member pro Dimension.
- `output/taxonomy-visualization-enum-domain-validity.html` — Enumerations-Domain-Validitaet mit Allowed-Value-Signalen.
- `output/taxonomy-visualization-dashboard.html` — zentraler Dashboard-Einstieg fuer alle Visualisierungsansichten.

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
