# Anleitung: iXBRL-Tag händisch aus einem ESRS-Datenpunkt ableiten

## 1. Ziel

Diese Anleitung beschreibt, wie man für einen ESRS-Datenpunkt manuell den passenden iXBRL-Tag ableitet.

Sie ist bewusst nicht programmatisch aufgebaut, sondern als Arbeitsanweisung für die manuelle Analyse der Taxonomie.

Das Ziel ist, für einen gegebenen Datenpunkt sicher zu bestimmen:

* ob `ix:nonNumeric` oder `ix:nonFraction` verwendet werden muss
* welche Pflichtattribute der Fact braucht
* wie der Fact-Inhalt aussehen muss
* welche externen Schemas, Linkbases und Enumerationen dabei relevant sind

---

## 2. Grundprinzip

Die Ableitung erfolgt immer von außen nach innen:

1. Einstiegsschema bestimmen
2. Eigentliche Konzeptdefinition finden
3. XBRL-Datentyp bestimmen
4. Daraus die iXBRL-Tag-Klasse ableiten
5. Zusätzliche Pflichtattribute bestimmen
6. Falls nötig Enumerationen, Dimensionen, Units und Referenzen prüfen
7. Erst am Ende den finalen iXBRL-Fact formulieren

---

## 3. Wichtige Dateien in dieser Taxonomie

### 3.1 Einstiegsschema

Der normale Einstiegspunkt ist:

* [xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd)

Dieses Schema ist wichtig, weil es:

* das eigentliche Concepts-Schema importiert
* Linkbases referenziert
* Formula-Dateien für Validierung einbindet

Wichtige Stelle:

* Import des Concepts-Schemas in [xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd#L264)

### 3.2 Concepts-Schema

Die eigentlichen Konzeptdefinitionen stehen in:

* [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd)

Hier stehen die entscheidenden Informationen für die Tag-Ableitung:

* `type`
* `abstract`
* `substitutionGroup`
* `xbrli:periodType`
* bei Enumerationen zusätzlich `enum2:domain`, `enum2:linkrole`, `enum2:headUsable`

### 3.3 Wichtige Linkbase-Arten

Je nach Datenpunkt sind zusätzlich relevant:

* `common/references/ref_esrs.xml`
* `all/enumerations/def_esrs_*.xml`
* `all/linkbases/def_esrs_*.xml`
* `all/formula/*.xml`

---

## 4. Externe referenzierte Schemas und warum sie relevant sind

### 4.1 XBRL Instance

Namespace:

* `http://www.xbrl.org/2003/instance`

Relevanz:

* Basis für `xbrli:item`
* liefert Standardtypen wie:
  * `booleanItemType`
  * `monetaryItemType`
  * `stringItemType`
* bestimmt die Logik von:
  * `context`
  * `unit`
  * `periodType`

Wenn ein Konzept z. B. `xbrli:monetaryItemType` ist, folgt daraus direkt, dass später `ix:nonFraction` und `unitRef` relevant sind.

### 4.2 DTR Type Registry

Namespaces in dieser Taxonomie:

* `http://www.xbrl.org/dtr/type/2022-03-31`
* `http://www.xbrl.org/dtr/type/2024-01-31`

Relevanz:

* liefert spezialisierte Typen wie:
  * `textBlockItemType`
  * `domainItemType`
  * weitere fachspezifische Typen

Wichtig:

* Im Einstiegsschema kann eine andere DTR-Version stehen als im Concepts-Schema.
* Für die konkrete Ableitung ist die Typbindung in der tatsächlichen Konzeptdefinition maßgeblich.

### 4.3 XBRL Dimensions

Namespace:

* `http://xbrl.org/2005/xbrldt`

Relevanz:

* Dimensionen
* Axes
* Hypercubes
* Domain-Member-Beziehungen
* Grundlage auch für viele Enumeration-Strukturen

Bei Enumerationen und Dimensionskontexten ist dieses Schema indirekt sehr wichtig.

### 4.4 Extensible Enumerations 2.0

Namespace:

* `http://xbrl.org/2020/extensible-enumerations-2.0`

Relevanz:

* `enumerationItemType`
* `enumerationSetItemType`
* `enum2:domain`
* `enum2:linkrole`

Wenn ein Konzept einer dieser Typen ist, wird es als `ix:nonNumeric` berichtet, aber mit speziell eingeschränkten zulässigen Werten.

### 4.5 Linkbase und XLink

Namespaces:

* `http://www.xbrl.org/2003/linkbase`
* `http://www.w3.org/1999/xlink`

Relevanz:

* Definition-Linkbases
* Reference-Linkbases
* Presentation/Calculation-Beziehungen

Diese Schemas sind nötig, um Domain-Hierarchien, Referenzparagraphen und Datapoint-Verknüpfungen zu verstehen.

---

## 5. Manuelle Ableitung Schritt für Schritt

## 5.1 Den exakten Datenpunktnamen bestimmen

Zuerst braucht man den exakten ESRS-Konzeptnamen, zum Beispiel:

* `FinancialResourcesAllocatedToActionPlanOpEx`
* `TargetCoverage`
* `DisclosureOfHowPolicyRefersToRecognisedStandardsOrThirdpartyCertificationsOverseenByRegulatorsExplanatory`

Ohne den exakten Namen ist keine saubere Herleitung möglich.

---

## 5.2 Die Elementdefinition im Concepts-Schema suchen

Suche in [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd) nach:

```xml
<xsd:element name="..."
```

Dabei interessieren die Attribute:

* `type`
* `abstract`
* `substitutionGroup`
* `xbrli:periodType`
* ggf. `enum2:domain`
* ggf. `enum2:linkrole`

Beispielhafte Typen:

* `xbrli:booleanItemType`
* `xbrli:monetaryItemType`
* `dtr-types:textBlockItemType`
* `enum2:enumerationSetItemType`

---

## 5.3 Prüfen, ob das Konzept überhaupt berichtbar ist

Wenn `abstract="true"` gesetzt ist, ist das Konzept normalerweise **kein direkt berichtbarer Fact**.

Typische Fälle:

* `...Axis`
* `...Member`
* `...Table`
* `...Abstract`

Dann ist dieses Konzept nur Strukturträger, aber nicht das eigentliche Fact-Konzept.

Merksatz:

* Nur `abstract="false"`-Konzepte kommen als eigentliche `ix:*`-Fakten in Frage.

---

## 5.4 Aus dem Typ die ix-Tag-Klasse ableiten

Das ist der zentrale Schritt.

### Nicht numerische Typen

Diese führen zu:

* `ix:nonNumeric`

Typische Beispiele:

* `xbrli:booleanItemType`
* `xbrli:stringItemType`
* `dtr-types:textBlockItemType`
* `enum2:enumerationItemType`
* `enum2:enumerationSetItemType`

### Numerische Typen

Diese führen zu:

* `ix:nonFraction`

Typische Beispiele:

* `xbrli:monetaryItemType`
* Prozenttypen
* Energie-, Mengen-, Massen-, Volumen- oder ähnliche numerische DTR-Typen

Faustregel:

* Text, Boolean, Enumeration -> `ix:nonNumeric`
* Zahlen -> `ix:nonFraction`

---

## 5.5 Periodentyp ablesen

Das Attribut `xbrli:periodType` bestimmt die zulässige Art des Contexts.

### `duration`

Der Context braucht:

* `startDate`
* `endDate`

### `instant`

Der Context braucht:

* `instant`

Wenn der falsche Context-Typ verwendet wird, ist der Fact ungültig.

---

## 5.6 Numerische Fakten: Unit prüfen

Wenn das Konzept numerisch ist, muss geprüft werden, ob eine Unit erforderlich ist.

Typischer Fall:

* `xbrli:monetaryItemType` -> `unitRef` erforderlich

In einem XHTML/iXBRL-Dokument muss die referenzierte Unit in den Ressourcen definiert sein.

Beispielhaft sieht das so aus:

```xml
<xbrli:unit id="u_EUR">
  <xbrli:measure>iso4217:EUR</xbrli:measure>
</xbrli:unit>
```

Die Unit muss inhaltlich zum Konzept passen.

---

## 5.7 Numerische Fakten: Genauigkeit prüfen

Numerische Fakten brauchen in der Regel zusätzlich:

* `decimals`
  oder
* `precision`

In der Praxis wird fast immer `decimals` verwendet.

Optional können hinzukommen:

* `scale`
* `format`

Beispiel:

```xml
<ix:nonFraction name="esrs:FinancialResourcesAllocatedToActionPlanOpEx" contextRef="c-12" unitRef="u_EUR" decimals="0" format="ixt4:num-dot-decimal">10000</ix:nonFraction>
```

---

## 5.8 Textliche Fakten: Sprache und Escape prüfen

Für textliche Fakten ist zusätzlich zu entscheiden:

* `xml:lang`
* `escape="true"` oder `escape="false"`

Typischerweise:

* Freitext oder Textblock -> `xml:lang` setzen
* je nach Inhalt `escape` bewusst wählen

Bei reinen Boolean-Facts ist das oft nicht nötig.

---

## 5.9 Enumeration-Fakten: Domain und Linkrole auswerten

Wenn das Konzept `enum2:enumerationItemType` oder `enum2:enumerationSetItemType` ist, reicht der bloße Typ nicht aus.

Zusätzlich müssen ausgewertet werden:

* `enum2:domain`
* `enum2:linkrole`

Dann muss die passende Enumeration-Linkbase geöffnet werden.

Beispiel:

* `TargetCoverage` in [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd#L7657)
* Domain-Head `TargetCoverageMember` in [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd#L7659)
* zugehörige Linkbase [xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/enumerations/def_esrs_999141.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/enumerations/def_esrs_999141.xml#L7)

---

## 5.10 Bei Enumerationen die erlaubten Member bestimmen

In der Enumeration-Linkbase werden die zulässigen Werte über `domain-member`-Beziehungen festgelegt.

Dazu suchst du:

1. den Domain-Head
2. die direkten und indirekten untergeordneten Member
3. die vollständige Hierarchie

Wichtig:

* Nicht ein Label berichten
* Nicht einen frei erfundenen Text berichten
* Nur taxonomisch zulässige Member aus der Domain-Hierarchie verwenden

---

## 5.11 Zwischen single und set unterscheiden

### `enum2:enumerationItemType`

* genau ein Member zulässig

### `enum2:enumerationSetItemType`

* mehrere Member zulässig

Das beeinflusst die Wertdarstellung im Fact.

---

## 5.12 Reference-Linkbase zur fachlichen Einordnung prüfen

Im nächsten Schritt sollte die Reference-Linkbase geprüft werden:

* [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml)

Dort stehen typischerweise:

* `ref:Number`
* `ref:Paragraph`
* `ref:Section`
* `ref:URI`
* `esrs:ReferenceType`
* `esrs:DatapointId`

Diese Informationen helfen, den fachlichen Bezug zu prüfen:

* Ist das Konzept Main oder Overlapping?
* Welchem Datapoint entspricht es?
* Ist es Mandatory, Related oder Conditional?

Wichtig:

* Die Reference-Linkbase bestimmt in der Regel **nicht** die ix-Tag-Klasse.
* Sie bestätigt aber die fachliche Zuordnung.

---

## 5.13 Dimensions prüfen, falls der Fact nur in bestimmtem Kontext sinnvoll ist

Wenn ein Fact über Dimensionen kontextualisiert wird, müssen zusätzlich Dimensions- oder Definition-Linkbases ausgewertet werden.

Signale dafür sind:

* Axis-Konzepte
* Hypercube-Konzepte
* `xbrldt:dimensionItem`
* `xbrldt:hypercubeItem`

Das ist wichtig, weil ein formal korrektes `ix:nonNumeric` oder `ix:nonFraction` fachlich trotzdem unvollständig sein kann, wenn der zugehörige Context nicht die erwarteten Dimensionen enthält.

---

## 5.14 Formula-Linkbases mitdenken

Im Einstiegsschema werden Formula-Dateien eingebunden, zum Beispiel:

* Mandatory Tags
* Unit Validations
* Typed Dimensions

Diese Dateien sind wichtig, weil sie zusätzliche Prüfregeln enthalten, die über die reine XSD-Struktur hinausgehen.

Beispiele:

* Pflichtfelder
* zulässige Units
* formale Kombinationsregeln

---

## 6. Typische Ableitungsfälle

## 6.1 Boolean

Beispieltyp:

* `xbrli:booleanItemType`

Ableitung:

* `ix:nonNumeric`

Beispiel:

```xml
<ix:nonNumeric name="esrs:SomeBooleanConcept" contextRef="c-1">true</ix:nonNumeric>
```

---

## 6.2 Textblock

Beispieltyp:

* `dtr-types:textBlockItemType`

Ableitung:

* `ix:nonNumeric`

Beispiel:

```xml
<ix:nonNumeric name="esrs:SomeTextBlockConcept" contextRef="c-1" xml:lang="de" escape="true">...</ix:nonNumeric>
```

---

## 6.3 Monetary

Beispieltyp:

* `xbrli:monetaryItemType`

Ableitung:

* `ix:nonFraction`
* `unitRef` erforderlich
* `decimals` oder `precision` erforderlich

Beispiel:

```xml
<ix:nonFraction name="esrs:SomeAmount" contextRef="c-1" unitRef="u_EUR" decimals="0" format="ixt4:num-dot-decimal">1000</ix:nonFraction>
```

---

## 6.4 Enumeration Single

Beispieltyp:

* `enum2:enumerationItemType`

Ableitung:

* `ix:nonNumeric`
* genau ein zulässiger Domain-Member als Wert

---

## 6.5 Enumeration Set

Beispieltyp:

* `enum2:enumerationSetItemType`

Ableitung:

* `ix:nonNumeric`
* mehrere Domain-Member als Wert möglich

---

## 7. Wichtige Sonderregel bei Enumeration-Werten

Bei Enumeration-Facts wird häufig angenommen, dass eine URL im Browser direkt auf einen konkreten HTML-Anker zeigen müsse.

Das ist nicht erforderlich.

Wichtig ist:

* Der Wert muss taxonomisch ein zulässiger Member der Domain sein.
* Die Browser-Darstellung der URL ist dafür nicht ausschlaggebend.

Ein Fragment wie `#esrs_PollutionOfAirMember` verändert den HTTP-Request nicht. Es ist clientseitige Fragment-Navigation.

Die Gültigkeit des Werts kommt aus:

* Concepts-Schema
* Domain-Head
* Enumeration-Linkbase
* Domain-Member-Kette

Nicht aus der Frage, ob ein Webserver den Fragmentanker hübsch rendert.

---

## 8. Typische Fehler

Häufige Fehlerquellen:

1. Ein `abstract="true"`-Konzept direkt als Fact berichten
2. `ix:nonFraction` für Boolean oder Enumeration verwenden
3. Bei monetären Facts `unitRef` vergessen
4. Bei numerischen Facts `decimals` vergessen
5. Falschen Context-Typ verwenden (`instant` statt `duration` oder umgekehrt)
6. Enumeration-Werte aus Labels statt aus der Domain-Hierarchie ableiten
7. Reference-Linkbase mit Typdefinition verwechseln
8. `ixt4:` verwenden, ohne das Namespace-Prefix korrekt zu deklarieren
9. Eine URL im Browser testen und aus der Webdarstellung falsche Schlüsse über die Taxonomiegültigkeit ziehen

---

## 9. Entscheidungsbaum in Kurzform

### Schritt 1

Konzept in `esrs_cor.xsd` finden.

### Schritt 2

Prüfen:

* `abstract`?
* `type`?
* `periodType`?

### Schritt 3

Tag-Klasse bestimmen:

* Text / Boolean / Enumeration -> `ix:nonNumeric`
* Zahl / Monetary / quantitative Typen -> `ix:nonFraction`

### Schritt 4

Pflichtattribute ableiten:

* immer `name`
* immer `contextRef`
* bei numerischen Fakten zusätzlich `unitRef`
* bei numerischen Fakten zusätzlich `decimals` oder `precision`

### Schritt 5

Falls Enumeration:

* `enum2:domain` lesen
* `enum2:linkrole` lesen
* passende Enumeration-Linkbase öffnen
* zulässige Member bestimmen

### Schritt 6

Reference-Linkbase lesen:

* fachliche Zuordnung
* DatapointId
* Main / Related / Overlapping

### Schritt 7

Bei Bedarf Dimensions- und Formula-Regeln prüfen.

---

## 10. Praktische Minimal-Checkliste

Vor dem finalen Fact immer prüfen:

1. Ist das Konzept `abstract="false"`?
2. Welcher Typ steht im XSD?
3. Ist der Fact numerisch oder nicht numerisch?
4. Welcher `periodType` gilt?
5. Braucht der Fact `unitRef`?
6. Braucht der Fact `decimals`?
7. Ist `xml:lang` sinnvoll?
8. Ist `escape` relevant?
9. Falls Enumeration: stammt der Wert sicher aus der richtigen Domain-Linkbase?
10. Passt der Context technisch und fachlich?

---

## 11. Beispielhafte Denkweise

### Fall A: Monetary-Fact

Wenn in `esrs_cor.xsd` steht:

* `type="xbrli:monetaryItemType"`

dann folgt:

* `ix:nonFraction`
* `unitRef` erforderlich
* `decimals` erforderlich

### Fall B: Textblock

Wenn in `esrs_cor.xsd` steht:

* `type="dtr-types:textBlockItemType"`

dann folgt:

* `ix:nonNumeric`
* kein `unitRef`
* `xml:lang` meist sinnvoll

### Fall C: Enumeration-Set

Wenn in `esrs_cor.xsd` steht:

* `type="enum2:enumerationSetItemType"`

dann folgt:

* `ix:nonNumeric`
* kein `unitRef`
* Wert muss aus Domain und Enumeration-Linkbase kommen

---

## 12. Schlussfolgerung

Ein iXBRL-Tag wird nie nur aus dem Datapoint-Namen allein korrekt abgeleitet.

Für eine saubere manuelle Ableitung müssen immer berücksichtigt werden:

* Einstiegsschema
* Concepts-Schema
* externer Typ-Namespace
* Periodentyp
* Units bei numerischen Fakten
* Enumerations-Linkbases bei enum2-Konzepten
* Reference-Linkbase zur fachlichen Einordnung
* bei Bedarf Dimensionen und Formula-Regeln

Das sichere Vorgehen ist daher:

1. Konzeptdefinition lesen
2. Typ verstehen
3. Enumerationen und Linkbases auswerten
4. Kontext- und Unit-Anforderungen prüfen
5. Finalen `ix:*`-Fact erst ganz am Ende formulieren