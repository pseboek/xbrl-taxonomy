# Anleitung: Start, Maven, Tests und Template-HTML

Stand: 2026-07-22

## 1. Ziel dieser Anleitung

Diese Anleitung beschreibt den praktischen Betrieb des Java-25-Projekts fuer ESRS XBRL/iXBRL:

- Projekt starten,
- Maven-Befehle sinnvoll nutzen,
- Tests ausfuehren,
- Pipeline laufen lassen,
- Template-HTML korrekt aufbauen und pflegen.

Sie ist als operatives Nachschlagewerk fuer Entwicklung und QA gedacht.

## 2. Voraussetzungen

Empfohlen:

- Java 25 installiert und in PATH,
- Maven 3.9+ installiert und in PATH,
- PowerShell (Windows),
- optional: Arelle CLI fuer echte Validierung,
- optional: iXBRL Viewer Plugin in Arelle fuer interaktive HTML-Ausgabe.

Pruefen:

```powershell
java -version
mvn -version
```

## 3. Projekt lokal starten

In das Projektverzeichnis wechseln:

```powershell
Set-Location "C:\Users\psebo\Desktop\ESRS-Set1-XBRL-Taxonomy"
```

Build + Tests:

```powershell
mvn test
```

Pipeline starten (Standard: Arelle uebersprungen):

```powershell
$env:SKIP_ARELLE = "true"
mvn exec:java
```

Erwartete Artefakte in output:

- report-instance.xml
- report-ixbrl.xhtml
- report-interaktiv.html
- arelle-xbrl.log
- arelle-ixbrl.log

## 4. Wichtige Umgebungsvariablen

Die Anwendung liest folgende Variablen:

- ARELLE_CMD:
  - Standard: arelleCmdLine
  - Pfad/Command fuer Arelle CLI
- SKIP_ARELLE:
  - true: Arelle-Pruefung ueberspringen
  - false: Arelle ausfuehren
- FAIL_ON_VALIDATION_ISSUES:
  - true: Validation Gate blockiert bei Fehlern
  - false: Fehler werden protokolliert, Lauf bricht nicht hart ab
- REQUIRE_VIEWER_PLUGIN:
  - true: Fallback bei Viewer-Ausgabe ist nicht erlaubt
  - false: Fallback-HTML erlaubt

Beispiel strikter Lauf:

```powershell
$env:ARELLE_CMD = "arelleCmdLine"
$env:SKIP_ARELLE = "false"
$env:FAIL_ON_VALIDATION_ISSUES = "true"
$env:REQUIRE_VIEWER_PLUGIN = "true"
mvn exec:java
```

Alternativ als Einzeiler ueber das Projektskript:

```powershell
./scripts/run-strict-production-gate.ps1 -ArelleCmd "arelleCmdLine"
```

Hinweis:

- Das Skript setzt automatisch:
  - SKIP_ARELLE=false
  - FAIL_ON_VALIDATION_ISSUES=true
  - REQUIRE_VIEWER_PLUGIN=true
- Ohne verfuegbares Arelle + iXBRL-Viewer-Plugin bricht das Skript absichtlich mit Fehler ab.

## 5. Maven-Kurzreferenz

Haefig genutzte Befehle:

```powershell
mvn clean
mvn compile
mvn test
mvn -DskipTests package
mvn exec:java
```

Nuetzliche Varianten:

Nur eine Testklasse:

```powershell
mvn -Dtest=FactBuilderTest test
```

Einzelner Testfall:

```powershell
mvn -Dtest=FactBuilderTest#shouldRejectInvalidNumericValue test
```

Coverage-Profil (falls benoetigt):

```powershell
mvn -Pcoverage verify
```

## 6. Tests ausfuehren und interpretieren

Testarten im Projekt:

- Unit-Tests:
  - Mapping, FactBuilder, Parser/Validator
- End-to-End-Tests:
  - Pipeline-Orchestrierung (inkl. iXBRL-Erzeugung)
- Konsistenztests:
  - Template-Placeholder gegen Layout-Mapping gegen Mapping-Datei gegen fiktive Input-Daten

Wichtige Regeln:

- Ein Testlauf ist nur erfolgreich, wenn alle Tests gruene Ergebnisse liefern.
- Bei Arelle-abhängigen Tests in lokalen Umgebungen ohne Plugin kann Fallback bewusst auftreten.
- Bei produktiver Qualitaetssicherung REQUIRE_VIEWER_PLUGIN=true setzen.

## 7. Wie ist die Pipeline aufgebaut

Eingang und Konfiguration:

- Input JSON:
  - src/main/resources/testdata/fictive-esrs-input.json
- Mapping:
  - mapping/map-esrs-2023-12-22.json
- Layout-Mapping:
  - mapping/report-layout-map.json
- HTML-Template:
  - templates/report-base.xhtml

Ablauf:

1. JSON einlesen
2. Mapping auf Fakten anwenden
3. Kontexte bilden
4. XBRL schreiben
5. Fakten in XHTML-Template einbetten
6. optional Arelle validieren
7. optional Viewer-HTML exportieren

## 8. Template-HTML: Wie muss die Datei aussehen

Pflichtanforderungen fuer templates/report-base.xhtml:

- Muss valides XHTML sein,
- UTF-8 Meta-Tag enthalten,
- Namespace fuer Inline XBRL enthalten,
- Placeholder im Format {{fact:key}} verwenden,
- Platzhalter-Schluessel muessen exakt in mapping/report-layout-map.json existieren.

Minimalbeispiel:

```xhtml
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:ix="http://www.xbrl.org/2013/inlineXBRL"
      xmlns:esrs="https://xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs"
      lang="de">
<head>
  <meta charset="utf-8" />
  <title>ESRS Bericht - {{entityName}}</title>
</head>
<body>
  <h1>ESRS Bericht</h1>
  <p>Energieverbrauch: {{fact:energyConsumption}}</p>
  <p>Korruptionsrichtlinie: {{fact:corruptionPolicy}}</p>
</body>
</html>
```

## 9. Wichtige Regeln fuer Template und Daten

### 9.1 Placeholder-Regeln

- Jeder {{fact:key}}-Platzhalter muss in mapping/report-layout-map.json vorhanden sein.
- Jeder Feldname aus report-layout-map.json muss ueber MappingRegistry aufloesbar sein (direkt in mapping/map-esrs-2023-12-22.json oder ueber dessen imports, z. B. mapping/domains/*.json).
- Jeder dieser Felder sollte fuer Testlaeufe in fictive-esrs-input.json vorkommen.

### 9.2 Fachliche Regeln

- Numerische Fakten:
  - muessen numerisch parsebar sein,
  - muessen eine Einheit haben,
  - muessen periodenkonsistent sein.
- Enumerationen:
  - nur erlaubte Domainwerte verwenden (z. B. YesNoDomain).
- Textwerte:
  - duerfen nicht leer sein.

### 9.3 iXBRL-spezifische Hinweise

- Die Einbettung erzeugt ix:nonFraction oder ix:nonNumeric.
- Im finalen report-ixbrl.xhtml duerfen keine rohen {{fact:...}}-Marker uebrig bleiben.
- JavaScript/CSS duerfen Struktur nicht so veraendern, dass die iXBRL-Semantik verloren geht.

## 10. Typische Fehlerbilder und Loesungen

1. Fehler: Missing mapping for field
- Ursache: Feld in Input, aber nicht in map-esrs-2023-12-22.json.
- Loesung: Mapping-Eintrag anlegen.

2. Fehler: Invalid numeric value
- Ursache: Text in numerischem Feld.
- Loesung: Inputwert korrigieren (z. B. 1234.56).

3. Fehler: Period mismatch
- Ursache: Mapping period=duration, Report aber instant (oder umgekehrt).
- Loesung: Berichtperiode und Mapping angleichen.

4. Fehler: Viewer fallback used
- Ursache: Plugin nicht verfuegbar.
- Loesung: Arelle Viewer Plugin installieren/aktivieren, REQUIRE_VIEWER_PLUGIN=true fuer harte Qualitaetsgrenze.

## 11. Empfohlener Arbeitsablauf

1. Mapping aendern
2. Template/Layout konsistent halten
3. fiktive Testdaten aktualisieren
4. mvn test ausfuehren
5. mvn -Pcoverage verify ausfuehren
6. mvn exec:java ausfuehren
7. output Artefakte pruefen
8. Branch -> Test -> Merge -> Tag

## 12. Relevante Dateien im Projekt

- pom.xml
- src/main/java/org/esrs/pipeline/EsrsPipelineApplication.java
- src/main/java/org/esrs/pipeline/orchestration/ReportingPipelineOrchestrator.java
- src/main/java/org/esrs/pipeline/xbrl/fact/FactBuilder.java
- mapping/map-esrs-2023-12-22.json
- mapping/domains/environment.json
- mapping/domains/social.json
- mapping/domains/governance.json
- mapping/report-layout-map.json
- templates/report-base.xhtml
- src/main/resources/testdata/fictive-esrs-input.json
- src/test/java/org/esrs/pipeline/orchestration/ReportingPipelineOrchestratorTest.java
- src/test/java/org/esrs/pipeline/ixbrl/template/TemplateDataConsistencyTest.java

Diese Datei kann als operative Start- und Betriebsanleitung fuer neue Entwickler und fuer QA verwendet werden.