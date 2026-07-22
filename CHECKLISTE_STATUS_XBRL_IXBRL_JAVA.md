# Checkliste und Statusbericht: XBRL/iXBRL Java-Umsetzung

Stand: 2026-07-22

## Zweck

Diese Checkliste bündelt den aktuellen Stand über alle vorhandenen Markdown-Dokumente und den Umsetzungsstatus für:

- XBRL-Instanzierung,
- iXBRL/XHTML-Erzeugung auf Basis eines Musterberichts,
- HTML-Konvertierung über Arelle iXBRL-Viewer,
- Java-25-Implementierung.

Sie dient gleichzeitig als:

- Management-Statusübersicht,
- Abstimmungsgrundlage zwischen Fachbereich und Technik,
- Steuerungsinput für eine Coding-KI.

## Statuslegende

- `Erfuellt`: vollständig vorhanden bzw. umgesetzt
- `Teilweise`: vorhanden, aber noch nicht vollständig operationalisiert
- `Offen`: noch nicht vorhanden

## A. Dokumentationsabdeckung (Markdown-Dateien)

| Bereich | Hauptdokument | Status | Hinweis |
| --- | --- | --- | --- |
| Projektinventur und Struktur | `PROJEKTSTRUKTUR.md` | Erfuellt | Struktur, Dateiprojektion, Fach-/Techniksicht, externe Verlinkungen dokumentiert |
| Technische Grundlagen der Taxonomie | `TECHNISCHE_GRUNDLAGEN.md` | Erfuellt | XML/XSD/Namespaces/Linkbases/Pipeline/Fehlerbilder dokumentiert |
| Begriffsdefinitionen | `GLOSSAR_XBRL_ESRS.md` | Erfuellt | Kernbegriffe für Fach- und Technikleser abgedeckt |
| Java-Implementierungszielbild | `IMPLEMENTIERUNGSLEITFADEN_JAVA_XBRL_ESRS.md` | Erfuellt | End-to-End-Architektur inkl. XBRL, iXBRL, Viewer beschrieben |
| KI-Ausführungsinstruktionen | `KI_INSTRUKTIONEN_XBRL_JAVA25.md` | Erfuellt | Verbindliche Vorgaben für Java 25, Arelle, iXBRL und Viewer vorhanden |

## B. Spezifikationsstatus (fachlich/technisch)

| Thema | Status | Evidenz |
| --- | --- | --- |
| ESRS-Taxonomiepaket und Entry Points beschrieben | Erfuellt | `PROJEKTSTRUKTUR.md`, `TECHNISCHE_GRUNDLAGEN.md` |
| Linkbases (pre/def/cal) fachlich und technisch eingeordnet | Erfuellt | `PROJEKTSTRUKTUR.md`, `TECHNISCHE_GRUNDLAGEN.md` |
| Dimensionen und Enumerationen erklärt | Erfuellt | alle Kern-Dokumente |
| Reference-Traceability zur Normquelle | Erfuellt | `PROJEKTSTRUKTUR.md`, `TECHNISCHE_GRUNDLAGEN.md` |
| Mehrsprachigkeitsaspekt eingeordnet | Erfuellt | `TECHNISCHE_GRUNDLAGEN.md` |
| Fachsicht vs Techniksicht inkl. Austauschpunkte | Erfuellt | `PROJEKTSTRUKTUR.md` |
| Java-Zielarchitektur für XBRL | Erfuellt | `IMPLEMENTIERUNGSLEITFADEN_JAVA_XBRL_ESRS.md` |
| iXBRL/XHTML-Zielarchitektur | Erfuellt | `IMPLEMENTIERUNGSLEITFADEN_JAVA_XBRL_ESRS.md`, `KI_INSTRUKTIONEN_XBRL_JAVA25.md` |
| Arelle iXBRL-Viewer-Konvertierung | Erfuellt | `IMPLEMENTIERUNGSLEITFADEN_JAVA_XBRL_ESRS.md`, `KI_INSTRUKTIONEN_XBRL_JAVA25.md` |

## C. Implementierungsstatus im Repository (Ist)

| Deliverable | Ziel | Status | Bemerkung |
| --- | --- | --- | --- |
| Java-Projektgerüst (Maven, Java 25) | Buildbare Codebasis | Erfuellt | `pom.xml` + `src/main/java` + `mvn test` vorhanden |
| Mapping-Registry (Feld -> Konzept) | Maschinenlesbares Mapping | Erfuellt | `mapping/map-esrs-2023-12-22.json` + `MappingRegistry` implementiert |
| XBRL-Instance Builder | `report-instance.xml` erzeugen | Erfuellt | `XbrlInstanceWriter` + Pipeline erzeugt Artefakt |
| Template-Asset-Basis | Basis fuer XHTML/HTML-Generierung | Erfuellt | `templates/report-base.xhtml`, `templates/assets/report.css`, `templates/assets/report.js` angelegt |
| iXBRL-Template-Engine | Musterbericht + Inline XBRL | Erfuellt | `IxbrlTemplateRenderer` + `IxbrlEmbeddingService` implementiert |
| Arelle-Validator-Adapter | automatische Validierung | Erfuellt | `ArelleValidator` + `ValidationReportParser` implementiert |
| iXBRL-Viewer-Export | `report-interaktiv.html` | Erfuellt | `IxbrlViewerExporter` implementiert (inkl. Fallback) |
| CI-Gates (XBRL/iXBRL/Viewer) | automatisierte Qualitätssicherung | Erfuellt | GitHub-Workflow `.github/workflows/ci-xbrl-ixbrl-java.yml` angelegt |
| Fiktive JSON-Testdatei (Pflicht) | Frühtest Datapoint/Ausprägung | Erfuellt | `src/main/resources/testdata/fictive-esrs-input.json` angelegt |

## D. Pflichtartefakte für die Umsetzung

### D1. Spezifikationsartefakte

- [x] Taxonomiepaket lokal vorhanden
- [x] Dokumentierte Entry Points vorhanden
- [x] Implementierungs- und KI-Instruktionsdokumente vorhanden

### D2. Umsetzungsartefakte

- [x] `pom.xml` oder gleichwertige Build-Definition
- [x] Java-Quellstruktur für Ingestion/Mapping/XBRL/iXBRL/Validation
- [x] Mapping-Konfiguration (YAML/JSON)
- [x] Layout-Mapping (`mapping/report-layout-map.json`)
- [x] Fiktive JSON-Testdaten (Datenpunkt + Ausprägung)
- [x] Beispiel-XBRL-Instanz (`report-instance.xml`)
- [x] Musterbericht-XHTML-Template (`templates/report-base.xhtml`)
- [x] Template-Stylesheet (`templates/assets/report.css`)
- [x] Template-Skript (`templates/assets/report.js`)
- [x] Beispiel-iXBRL-XHTML (`report-ixbrl.xhtml`)
- [x] Arelle-Validierungsreport
- [x] Interaktive Viewer-HTML (`report-interaktiv.html`)

## E. Qualitäts- und Abnahmestatus

| Kriterium | Status | Nachweis |
| --- | --- | --- |
| XBRL technisch valide | Teilweise | Arelle-Live-Run ohne Fehlercode, Log aktuell ohne Meldungsinhalt |
| iXBRL technisch valide | Teilweise | Arelle-Live-Run ohne Fehlercode, Log aktuell ohne Meldungsinhalt |
| Viewer-Konvertierung erfolgreich | Teilweise | Viewer-Plugin in dieser Umgebung nicht verfuegbar, Fallback-HTML erzeugt |
| Enumerationen/Dimensionen korrekt gemappt | Erfuellt | Validierungsregeln im `FactBuilder` + Unit-Tests vorhanden |
| Reproduzierbarer End-to-End-Lauf | Erfuellt | Integrationstest `ReportingPipelineOrchestratorTest` + reproduzierbare Artefakte vorhanden |

## F. Priorisierte nächste Schritte (Implementierungsreihenfolge)

1. Mapping-Abdeckung auf weitere ESRS-Konzepte schrittweise ausbauen.
2. Fallback-Betrieb für Viewer-Export in Zielumgebung deaktivieren, sobald Plugin-Version fixiert ist.
3. Zusätzliche negative End-to-End-Szenarien (Arelle-Fehlerfälle) in Testdaten aufnehmen.

## G. KI-Input-Block (für direkte Nutzung)

Die folgende Kurzform kann als kompakter Arbeitsauftrag für eine Coding-KI genutzt werden:

```text
Ziel: Implementiere mit Java 25 und Maven eine Pipeline fuer ESRS-XBRL + iXBRL.
Start zwingend mit fiktiver JSON (Datenpunkt/Auspraegung).
Erstelle und pflege die Template-Basis selbst: templates/report-base.xhtml, templates/assets/report.css, templates/assets/report.js, mapping/report-layout-map.json.
Erzeuge report-instance.xml, report-ixbrl.xhtml und report-interaktiv.html.
Validiere XBRL und iXBRL mit Arelle.
Nutze Arelle iXBRL-Viewer fuer die HTML-Konvertierung.
Fokussiere auf korrektes Mapping (Konzept, Kontext, Einheit, Dimensionen, Enumerationen).
Pflege diese Status-Checkliste pro Iteration und aktualisiere offene/erfuellte Punkte.
```

## H. Kurzfazit

- Die Spezifikation und Zielarchitektur sind dokumentationsseitig weitgehend vollständig.
- Der Software-Umsetzungsstand im Repository ist aktuell noch vor der eigentlichen Implementierung.
- Diese Checkliste bildet die Brücke zwischen dokumentiertem Soll und technischem Ist und ist als laufender Statusbericht zu verwenden.
