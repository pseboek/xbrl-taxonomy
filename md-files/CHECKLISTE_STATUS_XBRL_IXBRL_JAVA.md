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
| Mapping-Registry (Feld -> Konzept) | Maschinenlesbares Mapping | Erfuellt | `MappingRegistry` implementiert, Hauptmapping mit Domain-Imports (`mapping/domains/*.json`), Scope-Validierung aktiv |
| XBRL-Instance Builder | `report-instance.xml` erzeugen | Erfuellt | `XbrlInstanceWriter` + Pipeline erzeugt Artefakt |
| Template-Asset-Basis | Basis fuer XHTML/HTML-Generierung | Erfuellt | `templates/report-base.xhtml`, `templates/assets/report.css`, `templates/assets/report.js` angelegt |
| iXBRL-Template-Engine | Musterbericht + Inline XBRL | Erfuellt | `IxbrlTemplateRenderer` + `IxbrlEmbeddingService` implementiert (inkl. dynamischer Vollfaktentabelle `{{facts:all}}`) |
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
| XBRL technisch valide | Erfuellt | Arelle-Validation-Gate implementiert (Fehler/fehlende Evidenz blockieren strikt) |
| iXBRL technisch valide | Erfuellt | iXBRL-Header wird verborgen gerendert (`display:none`), Strict Production Gate erfolgreich |
| Viewer-Konvertierung erfolgreich | Erfuellt | Strict Production Gate erfolgreich; Viewer-Export ohne Fallback bestaetigt (`Viewer fallback used: false`) |
| Enumerationen/Dimensionen korrekt gemappt | Erfuellt | `YesNoDomain`-Normalisierung + generische `allowedValues`-Validierung im `FactBuilder` + Unit-Tests |
| Reproduzierbarer End-to-End-Lauf | Erfuellt | Integrationstest `ReportingPipelineOrchestratorTest` + reproduzierbare Artefakte vorhanden |

## F. Priorisierte nächste Schritte (Implementierungsreihenfolge)

1. Scope-Liste in `mapping/scopes/esrs-full-scope.json` iterativ auf fachlich vollständigen Zielumfang je Disclosure Requirement ausbauen.
2. Bereitstellung des Arelle iXBRL-Viewer-Plugins in Zielumgebungen vereinheitlichen (Dev/CI/Prod).
3. Optional: Strict-Arelle-Gate in CI von "optional bei gesetzter Variable" auf verpflichtenden Lauf umstellen, sobald Arelle in CI stabil provisioniert ist.

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

- Die Spezifikation und Zielarchitektur sind dokumentationsseitig vollständig und als MVP technisch umgesetzt.
- Die Validierung wurde als technisches Gate gehärtet (Arelle-Fehler und fehlende Validierungs-Evidenz führen zu Fehlerzustand).
- Die Mapping-Abdeckung wurde gegenüber dem MVP deutlich erweitert, domain-spezifisch strukturiert und durch Scope-Checks abgesichert.
- Die Pipeline besitzt nun zentrale Konfiguration, strukturiertes Logging (SLF4J/Logback) und ein verpflichtendes Coverage-Gate in CI (`mvn -Pcoverage verify`).
- Der strict Productive-Gate-Run ist als Skript und optionaler CI-Job implementiert und läuft aktuell erfolgreich (`Viewer fallback used: false`).
