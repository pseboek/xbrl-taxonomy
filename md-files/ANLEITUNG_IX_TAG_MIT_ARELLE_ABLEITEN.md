# Anleitung: iXBRL-Tags mit Arelle aus ESRS-Datenpunkten ableiten

## 1. Ziel

Diese Anleitung beschreibt, wie man mit Arelle die Ableitung von iXBRL-Tags für ESRS-Datenpunkte nachvollziehen und absichern kann.

Das Ziel ist, für einen Datenpunkt oder Konzeptnamen systematisch zu bestimmen:

* ob `ix:nonNumeric` oder `ix:nonFraction` verwendet werden muss
* welcher XBRL-Konzepttyp tatsächlich vorliegt
* ob es sich um ein direkt berichtbares Konzept handelt
* welche Referenzen und DatapointIds damit verknüpft sind
* ob Enumeration-Linkbases, Units oder weitere Strukturen zu berücksichtigen sind

Die Anleitung ist bewusst praxisorientiert und auf dieses Repository zugeschnitten.

---

## 2. Grundidee

Mit Arelle wird die Ableitung nicht "erraten", sondern aus der geladenen Taxonomie ausgelesen.

Typischer Ablauf:

1. Einstiegsschema mit Arelle laden
2. Concepts-Liste exportieren
3. Gewünschtes Konzept in der Concepts-Liste suchen
4. Typ, PeriodType, Abstract-Flag und Namespace ablesen
5. Falls nötig Reference-Linkbase-Beziehungen exportieren
6. Falls Enumeration: passende Enumeration-Linkbase prüfen
7. Erst danach den finalen `ix:*`-Tag formulieren

---

## 3. Relevante Dateien im Repository

### 3.1 Arelle in diesem Projekt

Im Repository liegt Arelle bereits bei:

* `arelle/arelleCmdLine.exe`

Das ist der wichtigste Einstieg für die CLI-basierte Ableitung.

### 3.2 Einstiegsschema der Taxonomie

Der geeignete ESRS-Einstiegspunkt ist:

* [xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd)

Alternativ kann man auch direkt das Concepts-Schema laden:

* [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd)

Praktisch ist `esrs_all.xsd` der sauberere Einstieg, weil dort weitere Linkbases und Validierungsregeln zusammengeführt werden.

### 3.3 Output-Dateien, die Arelle erzeugen kann

Für die Ableitung besonders nützlich sind:

* `--csvConcepts`
* `--viewArcrole http://www.xbrl.org/2003/arcrole/concept-reference`
* optional weitere Linkbase-Views

---

## 4. Warum Arelle hier hilfreich ist

Arelle nimmt dir mehrere fehleranfällige manuelle Schritte ab:

* vollständiges Laden der Taxonomie
* Auflösen des Konzepts inklusive Namespace
* Auslesen des tatsächlichen Datentyps
* Anzeige, ob ein Konzept abstrakt ist
* Anzeige der Concept-References
* strukturierte Ausgabe statt manueller Roh-XSD-Suche

Damit ist Arelle besonders hilfreich für:

* große ESRS-Taxonomien
* Enumeration-Konzepte
* Konzepte mit mehreren Referenzen
* Konzepte, die mehreren Datapoints überlappend zugeordnet sind

---

## 5. Vorbedingungen

Für diese Anleitung wird angenommen:

1. Du arbeitest im Wurzelverzeichnis des Repositories.
2. Arelle CLI ist vorhanden unter `arelle/arelleCmdLine.exe`.
3. Die ESRS-Taxonomie liegt lokal im Projekt.

Hinweis zu Python:

* In diesem Repository ist die Arelle-CLI direkt nutzbar.
* Der direkte Import der gebündelten Arelle-Python-Module kann an Bytecode-/Python-Versionsunterschieden scheitern.
* Für die manuelle Arbeitsweise ist die CLI der robusteste Weg.

---

## 6. Standardworkflow mit Arelle CLI

## 6.1 Taxonomie laden und Concepts exportieren

Der wichtigste erste Schritt ist der Export aller Concepts.

Beispiel:

```powershell
./arelle/arelleCmdLine.exe --file xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd --csvConcepts output/arelle-concepts.csv --logLevel error
```

Danach erhältst du eine CSV-Datei mit Spalten wie:

* Label
* Name
* ID
* Namespace
* Abstract
* Substitution Group
* Type
* Period Type
* Nillable
* Facets

Diese Datei ist die wichtigste Grundlage für die Tag-Ableitung.

---

## 6.2 Das gewünschte Konzept in der Concepts-CSV suchen

Beispiel mit PowerShell:

```powershell
Select-String -Path output/arelle-concepts.csv -Pattern "FinancialResourcesAllocatedToActionPlanOpEx"
```

Dann liest du in der Trefferzeile:

* den exakten Konzeptnamen
* den Typ
* ob das Konzept abstrakt ist
* den Period Type

---

## 6.3 Aus der Concepts-CSV den ix-Tag-Typ ableiten

Die Regel ist dieselbe wie bei der manuellen Taxonomieanalyse:

### Wenn Type ist:

* `xbrli:booleanItemType`
* `xbrli:stringItemType`
* `dtr-types:textBlockItemType`
* `enum2:enumerationItemType`
* `enum2:enumerationSetItemType`

Dann folgt:

* `ix:nonNumeric`

### Wenn Type ist:

* `xbrli:monetaryItemType`
* andere quantitative numerische Typen

Dann folgt:

* `ix:nonFraction`

---

## 6.4 Abstract-Flag prüfen

Die Concepts-CSV zeigt, ob ein Konzept abstrakt ist.

Faustregel:

* `abstract=false` -> potentiell direkt berichtbarer Fact
* `abstract=true` -> Struktur-/Domain-/Axis-/Member-Konzept, in der Regel nicht direkt als Fact zu berichten

Das ist besonders wichtig bei:

* `...Member`
* `...Axis`
* `...Table`
* `...Abstract`

---

## 6.5 Period Type prüfen

In der Concepts-CSV steht auch der `Period Type`.

### `duration`

Benötigt einen Context mit:

* `startDate`
* `endDate`

### `instant`

Benötigt einen Context mit:

* `instant`

Wenn der Context nicht zum Period Type passt, ist der Fact ungültig, auch wenn der `ix`-Tag sonst korrekt wäre.

---

## 6.6 Reference-Beziehungen exportieren

Um fachliche Referenzen und DatapointIds mitzulesen, exportierst du die Concept-References.

Beispiel:

```powershell
./arelle/arelleCmdLine.exe --file xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd --viewArcrole http://www.xbrl.org/2003/arcrole/concept-reference --viewFile output/arelle-concept-reference.csv --relationshipCols Name,Namespace,LocalName,References --logLevel error
```

Diese Datei zeigt dir pro Konzept die verknüpften References.

Dort kannst du häufig sehen:

* Paragraphen
* Sections
* DatapointIds
* Main / Related / Overlapping

---

## 6.7 Datapoint-Bezug aus der Reference-Datei lesen

Suche das Konzept in `output/arelle-concept-reference.csv`.

Beispiel:

```powershell
Select-String -Path output/arelle-concept-reference.csv -Pattern "TargetCoverageMember|E4-4_06"
```

Damit kannst du nachvollziehen:

* welches Konzept welchem ESRS-Datapoint entspricht
* ob die Zuordnung Main oder Overlapping ist
* ob mehrere DatapointIds für dasselbe Konzept existieren

Wichtig:

* Die Concept-Reference-Datei dient der fachlichen Zuordnung.
* Die eigentliche Entscheidung `nonNumeric` vs `nonFraction` kommt weiterhin aus dem Concept-Type.

---

## 7. Enumeration-Facts mit Arelle herleiten

Enumeration-Facts sind der Bereich, in dem Arelle besonders hilfreich ist.

## 7.1 Enumeration am Concept-Type erkennen

Wenn in der Concepts-CSV steht:

* `enum2:enumerationItemType`
  oder
* `enum2:enumerationSetItemType`

dann ist klar:

* `ix:nonNumeric`

Aber das reicht noch nicht. Danach musst du die erlaubten Werte bestimmen.

---

## 7.2 Domain und Linkrole im XSD prüfen

Arelle zeigt in der Concepts-CSV den Typ, aber für die Domain-Details musst du zusätzlich in `esrs_cor.xsd` prüfen:

* `enum2:domain`
* `enum2:linkrole`

Beispielhaft:

```xml
enum2:domain="esrs:TargetCoverageMember"
enum2:linkrole="https://xbrl.efrag.org/taxonomy/role-999141"
```

---

## 7.3 Passende Enumeration-Linkbase öffnen

Mit `enum2:linkrole` identifizierst du die richtige Enumeration-Datei, z. B.:

* `all/enumerations/def_esrs_999141.xml`

Dort liest du:

* Domain-Head
* Domain-Member-Beziehungen
* zulässige Member

---

## 7.4 Werte aus der Domain-Hierarchie bestimmen

Du darfst nur Werte verwenden, die in dieser Domain-Hierarchie tatsächlich enthalten sind.

Je nach Konzept sind erlaubt:

* direkte Children des Domain-Heads
* oder tiefer liegende Unter-Member, wenn sie Teil derselben Member-Hierarchie sind

---

## 7.5 Single vs Set unterscheiden

### `enumerationItemType`

* ein einzelner Member

### `enumerationSetItemType`

* mehrere Member als Set

Das musst du beim Fact-Inhalt berücksichtigen.

---

## 8. Numerische Fakten mit Arelle herleiten

Wenn Arelle für ein Konzept `xbrli:monetaryItemType` oder einen anderen numerischen Typ ausgibt, folgt daraus:

* `ix:nonFraction`

Zusätzlich musst du manuell prüfen:

* `unitRef`
* `decimals` oder `precision`
* ggf. `scale`
* ggf. `format`

Das liest Arelle nicht automatisch als finalen fertigen Tag aus, sondern du leitest es aus dem numerischen Typ und der üblichen Fact-Struktur ab.

---

## 9. Text- und Boolean-Fakten mit Arelle herleiten

Wenn Arelle ausgibt:

* `xbrli:booleanItemType`
* `xbrli:stringItemType`
* `dtr-types:textBlockItemType`

dann folgt:

* `ix:nonNumeric`

Danach entscheidest du zusätzlich:

* ob `xml:lang` benötigt wird
* ob `escape="true"` oder `escape="false"` sinnvoll ist

---

## 10. Praktische Beispiele der Arelle-Ableitung

## 10.1 Boolean-Beispiel

Wenn in `output/arelle-concepts.csv` steht:

* Type = `xbrli:booleanItemType`

Dann lautet die technische Ableitung:

* `ix:nonNumeric`

Beispiel:

```xml
<ix:nonNumeric name="esrs:SomeBooleanConcept" contextRef="c-1">true</ix:nonNumeric>
```

---

## 10.2 Textblock-Beispiel

Wenn Arelle ausgibt:

* Type = `dtr-types:textBlockItemType`

Dann folgt:

* `ix:nonNumeric`

Beispiel:

```xml
<ix:nonNumeric name="esrs:SomeTextBlockConcept" contextRef="c-1" xml:lang="de" escape="true">...</ix:nonNumeric>
```

---

## 10.3 Monetary-Beispiel

Wenn Arelle ausgibt:

* Type = `xbrli:monetaryItemType`

Dann folgt:

* `ix:nonFraction`
* `unitRef` erforderlich
* `decimals` erforderlich

Beispiel:

```xml
<ix:nonFraction name="esrs:FinancialResourcesAllocatedToActionPlanOpEx" contextRef="c-12" unitRef="u_EUR" decimals="0" format="ixt4:num-dot-decimal">10000</ix:nonFraction>
```

---

## 10.4 Enumeration-Set-Beispiel

Wenn Arelle ausgibt:

* Type = `enum2:enumerationSetItemType`

Dann folgt:

* `ix:nonNumeric`

Beispiel:

```xml
<ix:nonNumeric name="esrs:TargetCoverage" contextRef="c-12">...</ix:nonNumeric>
```

Danach müssen die zulässigen Member aus der passenden Enumeration-Linkbase gelesen werden.

---

## 11. Unterschiede zwischen Arelle und reiner XSD-Handanalyse

### Reine XSD-Handanalyse

Vorteile:

* volle Kontrolle
* direkte Sicht auf Originalquellen

Nachteile:

* mühsam bei vielen Konzepten
* fehleranfällig bei großen Taxonomien

### Arelle-gestützte Analyse

Vorteile:

* strukturierte Ausgabe
* Concepts und References schnell filterbar
* Typen, Abstract-Flags und PeriodType sofort sichtbar

Nachteile:

* Enumeration-Linkbases und Spezialfragen müssen oft zusätzlich manuell gelesen werden
* Arelle baut nicht automatisch den finalen kompletten `ix`-Tag für dich

---

## 12. Typische Fehler beim Arbeiten mit Arelle

1. Nur die Reference-Datei anschauen und den Concept-Type nicht prüfen
2. Aus DatapointId direkt auf `ix:nonFraction` oder `ix:nonNumeric` schließen
3. `abstract=true` übersehen
4. Bei Enumeration-Type die Domain-Hierarchie nicht prüfen
5. Bei numerischen Typen `unitRef` vergessen
6. `decimals` bei numerischen Fakten nicht setzen
7. `periodType` ignorieren
8. Aus der Browser-Darstellung einer URI falsche Schlüsse über die taxonomische Gültigkeit ziehen

---

## 13. Empfohlener Standardprozess

Für die tägliche Arbeit empfiehlt sich folgender Ablauf:

1. Einstiegsschema oder Concepts-Schema mit Arelle laden
2. `--csvConcepts` erzeugen
3. Zielkonzept in der Concepts-CSV suchen
4. Type, Abstract und PeriodType auslesen
5. Falls nötig `--viewArcrole concept-reference` erzeugen
6. Datapoint-Bezüge und Referenzen prüfen
7. Falls Enumeration: Domain und Linkrole im XSD nachschlagen
8. Passende Enumeration-Linkbase manuell lesen
9. Pflichtattribute festlegen
10. Finalen `ix:*`-Fact formulieren

---

## 14. Kompakte Entscheidungslogik

### Arelle zeigt `xbrli:booleanItemType`

-> `ix:nonNumeric`

### Arelle zeigt `dtr-types:textBlockItemType`

-> `ix:nonNumeric`

### Arelle zeigt `xbrli:monetaryItemType`

-> `ix:nonFraction`
-> `unitRef` und `decimals` prüfen

### Arelle zeigt `enum2:enumerationItemType`

-> `ix:nonNumeric`
-> genau ein erlaubter Member

### Arelle zeigt `enum2:enumerationSetItemType`

-> `ix:nonNumeric`
-> Member-Set aus der Domain-Hierarchie

---

## 15. Schlussfolgerung

Mit Arelle lässt sich die Ableitung eines iXBRL-Tags deutlich robuster und schneller durchführen als nur per Rohdatei-Lesen.

Der wichtigste Nutzen von Arelle ist:

* das strukturierte Auffinden des Konzepts
* das sichere Auslesen des Typs
* das Auflösen der Concept-References

Die vollständige Ableitung ist aber trotzdem ein kombinierter Prozess aus:

1. Arelle-Concept-Analyse
2. Arelle-Reference-Analyse
3. manueller Prüfung von Enumeration-Linkbases
4. manueller Prüfung von Units, Contexts und Zusatzattributen

Damit ist Arelle das zentrale Analysewerkzeug, aber nicht der alleinige letzte Entscheidungsträger für den finalen iXBRL-Tag.