# ESRS XHTML Vergleichsanalyse

## 1. Überblick

Es wurden folgende XHTML-Dateien analysiert:

* ESRS2
* E1 – E5 (Umwelt)
* S1 – S4 (Soziales)
* G1 (Governance)

Ziel:
Vergleich der Struktur, Tags, Attribute und Unterschiede zwischen den Taxonomie-Themen.

---

# 2. Gemeinsame Struktur (alle Dateien)

## 2.1 XHTML Grundaufbau

Alle Dateien folgen derselben HTML-Struktur:

```xml
<html>
  <head>
    <meta />
    <title />
    <style />
  </head>
  <body>
    <div>, <span>, <p>
    <table>, <tr>, <td>
  </body>
</html>
```

### Gemeinsame HTML-Elemente

* Layout: `div`, `span`, `p`
* Tabellen: `table`, `tr`, `td`
* Formatierung: `br`, `hr`, `small`, `i`
* Struktur: `header`

👉 Diese dienen ausschließlich der Darstellung.

---

## 2.2 XBRL / iXBRL Kernstruktur

Alle Dateien enthalten dieselben XBRL-Elemente:

### Kontext

* `context`
* `entity`
* `identifier`
* `period`
* `startDate`, `endDate`
* `scenario`

### Dimensionen

* `explicitMember`
* `typedMember`

### Fakten

* `nonFraction` → numerische Werte
* `nonNumeric` → Textwerte

### Einheiten

* `unit`
* `measure`

---

## 2.3 Linking & Referenzen

* `schemaRef`
* `resources`
* `references`

---

## 2.4 Inline XBRL Features

* `continuation`
* `hidden`

---

## 2.5 Gemeinsame Attribute

### Identifikation

* `id`
* `name`
* `contextRef`
* `unitRef`

### Werteformatierung

* `decimals`
* `scale`
* `format`

### Dimensionen

* `dimension`

### Linking

* `xlink:href`
* `xlink:type`

---

## 2.6 Namespaces (immer vorhanden)

* `xmlns`
* `xmlns:ix`
* `xmlns:xbrli`
* `xmlns:xbrldi`
* `xmlns:link`
* `xmlns:esrs`
* `xmlns:iso4217`

---

# 3. Unterschiede zwischen den Themen

## 3.1 Grundsatz

👉 Die Struktur ist bei allen Dateien identisch.

Unterschiede bestehen ausschließlich in:

* verwendeten Namespaces
* Taxonomie-Elementen (`esrs:*`)
* Inhalten

---

## 3.2 ESRS2 (General Disclosures)

Besonderheit:

* zusätzliches Namespace:

  * `xmlns:ixt5`

👉 Hinweis auf erweiterte Inline-XBRL-Transformation.

---

## 3.3 E-Themen (E1–E5)

Gemeinsam:

* keine strukturellen Unterschiede

Besonderheit:

* **E3** enthält zusätzlich:

  * `sign` Attribut

👉 vermutlich für spezielle numerische Werte (z. B. Umweltmetriken)

---

## 3.4 S-Themen (S1–S4)

* keine strukturellen Unterschiede
* identischer Aufbau wie E und G

---

## 3.5 G1 (Governance)

* ebenfalls keine strukturellen Besonderheiten

---

# 4. Erweiterte Namespaces (entscheidender Unterschied)

## 4.1 Country Namespace

```xml
xmlns:country="https://xbrl.org/2024/iso3166"
```

### Bedeutung:

* ISO-3166 Ländercodes (DE, FR, etc.)

### Verwendung:

* geografische Dimensionen
* Länderbasierte Auswertungen

### Auftreten:

* nur wenn geografische Daten vorhanden sind

---

## 4.2 UTR Namespace

```xml
xmlns:utr="http://www.xbrl.org/2009/utr"
```

### Bedeutung:

* Unit Type Registry

### Verwendung:

* Validierung von Maßeinheiten
* komplexe Einheiten (z. B. tCO2e, m³)

### Auftreten:

* nur bei Bedarf für spezielle Units

---

## 4.3 Wichtiges Prinzip

👉 Namespaces werden **nur eingebunden, wenn sie benötigt werden**

---

# 5. Typische Verteilung nach Themen

## Umwelt (E1–E5)

* `country` → häufig vorhanden
* `utr` → häufig vorhanden

👉 komplexe physische und geografische Daten

---

## Soziales (S1–S4)

* `country` → teilweise vorhanden
* `utr` → selten

👉 hauptsächlich personenbezogene Daten

---

## Governance (G1)

* meist keine zusätzlichen Namespaces

👉 hauptsächlich Text und einfache Kennzahlen

---

## ESRS2

* gemischte Inhalte
* kann alle Namespaces enthalten

---

# 6. Technische Erkenntnisse

## 6.1 Einheitliches Template

Alle Dateien basieren auf derselben Struktur.

👉 Vorteil:

* ein Parser reicht für alle Themen

---

## 6.2 Unterschiede sind semantisch

Nicht unterschiedlich:

* HTML
* XBRL-Struktur

Unterschiedlich:

* Taxonomie-Inhalte (`esrs:*`)
* Dimensionen
* Fakten

---

## 6.3 Tabellen sind sekundär

* `<table>` dient nur Darstellung
* echte Daten liegen in:

  * `nonFraction`
  * `nonNumeric`

---

# 7. Parser-Implikationen

## 7.1 Namespaces dynamisch behandeln

Namespaces sind optional:

* `country` → evtl. vorhanden
* `utr` → evtl. vorhanden

👉 Parser darf keine festen Annahmen treffen

---

## 7.2 Keine festen Prefixe verwenden

❌ falsch:

```xpath
//country:DE
```

✅ richtig:

* Namespace URI nutzen
* oder `local-name()`

---

## 7.3 Optionalität berücksichtigen

```pseudo
if namespace vorhanden:
    verwenden
else:
    ignorieren
```

---

# 8. Architekturmodell

```text
Core XBRL
   + ESRS Taxonomy
   + (optional) Country Taxonomy
   + (optional) UTR
   + (optional) weitere Erweiterungen
```

---

# 9. Fazit

* Alle ESRS-XHTML-Dateien haben **identische technische Struktur**
* Unterschiede entstehen durch:

  * Inhalte
  * optionale Namespaces
* Das System ist **modular aufgebaut**

---

# 10. Wichtigste Erkenntnis

👉 Du brauchst:

* **einen generischen Parser**
* keine getrennte Logik für E, S, G

---

# 11. Nächste sinnvolle Schritte

* Extraktion aller `nonFraction` und `nonNumeric`

* Gruppierung nach:

  * Taxonomie (`esrs:*`)
  * Kontext (`contextRef`)
  * Dimensionen

* Optional:

  * Visualisierung (z. B. Graph / Cytoscape)
