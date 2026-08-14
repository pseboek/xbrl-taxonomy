# Glossar der technischen XBRL- und ESRS-Begriffe

Stand: 2026-08-14

Aktualisiert nach dem aktuellen Repository-Zustand. Die fachlichen und technischen Begriffe bleiben gültig; die Doku wurde auf den aktuelleren Projektstand und die vorhandenen Output-Artefakte abgestimmt.

Diese Datei ergänzt die technische Gesamtdokumentation um ein kompaktes, praxisnahes Glossar. Jeder Begriff wird mit Bezug auf die Dateien und Strukturen dieses Repositories erklärt.

## XML

XML ist das Grundformat fast aller Dateien in diesem Projekt. XML beschreibt verschachtelte Elemente und Attribute, aber noch keine fachliche Logik.

Beispiele im Repository:

- [META-INF/taxonomyPackage.xml](META-INF/taxonomyPackage.xml)
- [META-INF/catalog.xml](META-INF/catalog.xml)
- [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml)

## XSD

XSD ist die XML-Schemadefinition. Eine XSD-Datei beschreibt, welche XML-Strukturen erlaubt sind und welche weiteren Ressourcen eingebunden werden.

Im Projekt sind die wichtigsten XSD-Dateien die Entry Points:

- [xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd)
- [xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd)

## Namespace

Ein Namespace trennt Vokabulare, damit Namen nicht kollidieren. Das Projekt verwendet mehrere Namespaces gleichzeitig, zum Beispiel für XSD, XBRL-Linkbases, XLink, Dimensions und das ESRS-Fachvokabular.

Wichtige Beispiele:

- `https://xbrl.efrag.org/taxonomy/esrs/2023-12-22`
- `https://xbrl.efrag.org/taxonomy/esrs/2023-12-22/entry`
- `http://www.w3.org/2001/XMLSchema`
- `http://www.xbrl.org/2003/linkbase`
- `http://www.w3.org/1999/xlink`

## Taxonomy Package

Ein Taxonomy Package ist der äußere Container einer XBRL-Taxonomie. Es beschreibt Version, Publisher, Entry Points und Metadaten.

Im Projekt wird das Paket in [META-INF/taxonomyPackage.xml](META-INF/taxonomyPackage.xml) beschrieben.

## Entry Point

Ein Entry Point ist eine Startdatei, über die die Taxonomie geladen wird. Er entscheidet, wie viel vom Modell eingebunden wird.

In diesem Projekt gibt es zwei Entry Points:

- den vollständigen Einstieg über [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd)
- den reduzierten Core-Einstieg über [common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd)

## Linkbase

Eine Linkbase ist eine XML-Datei mit Beziehungen zwischen fachlichen XBRL-Konzepten. Linkbases sind der Ort, an dem die Taxonomie ihre innere Struktur ausdrückt.

Im Repository liegen die wichtigsten Linkbases unter [xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/).

### Presentation Linkbase

Presentation Linkbases definieren die Anzeigehierarchie. Sie beantworten die Frage, wie Konzepte im Baum angeordnet werden.

Beispiel: Dateien mit dem Präfix `pre_esrs_` in [all/linkbases/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/).

### Calculation Linkbase

Calculation Linkbases definieren Summen- und Roll-up-Beziehungen.

Beispiel: Dateien mit dem Präfix `cal_esrs_` in [all/linkbases/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/).

### Definition Linkbase

Definition Linkbases beschreiben semantische Beziehungen, insbesondere Domänen, Members und Dimensionen.

Beispiel: Dateien mit dem Präfix `def_esrs_` in [all/linkbases/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/).

## link:linkbaseRef

`link:linkbaseRef` ist die technische Verlinkung von einer XSD-Datei zu einer Linkbase.

In [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd) sieht man damit, wie die Taxonomie aus vielen einzelnen Ressourcen zusammengesetzt wird.

## xlink:href

`xlink:href` zeigt auf die Zielressource. In diesem Projekt verweisen solche Links oft auf relative Dateien im Taxonomiebaum.

Beispiel:

- [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd) verweist auf Linkbases und das Core-Schema.

## xlink:role

`xlink:role` beschreibt die fachliche Rolle einer Ressource oder Beziehung.

Beispiele im Projekt:

- `http://www.xbrl.org/2003/role/reference` in [ref_esrs.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml)
- `http://www.xbrl.org/2003/role/presentationLinkbaseRef` in [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd)

## xlink:arcrole

`xlink:arcrole` beschreibt die Art der Beziehung zwischen zwei Knoten.

Typische Werte im Repository:

- `http://www.xbrl.org/2003/arcrole/concept-reference`
- Linkbase- und Strukturbeziehungen innerhalb der Präsentations-, Berechnungs- und Definitionslinkbases

## roleType

`roleType` ist eine benutzerdefinierte fachliche Rolle, die festlegt, wie ein Abschnitt der Taxonomie logisch gegliedert ist.

Im Core-Schema [common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd) sind viele `roleType`-Einträge definiert, zum Beispiel für ESRS2-Basisangaben, Governance oder Strategie.

## Berichtsvorlage (Template-Basis)

Die Berichtsvorlage ist die technische Grundlage fuer die XHTML- und HTML-Generierung. Sie wird vom Coding-Agenten selbst erstellt und versioniert und umfasst mindestens:

- [templates/report-base.xhtml](templates/report-base.xhtml)
- [templates/assets/report.css](templates/assets/report.css)
- [templates/assets/report.js](templates/assets/report.js)
- [mapping/report-layout-map.json](mapping/report-layout-map.json)

## Dimensions

Dimensionen sind zusätzliche fachliche Achsen. Sie erlauben, dieselbe Information nach Kontexten wie Scope, Land, Segment oder Member zu differenzieren.

Im Projekt sind die Dimensionen in [all/dimensions/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/dimensions/) organisiert.

## Typed Dimension

Eine typed dimension ist eine Dimension, deren Wert nicht aus einer festen Liste kommt, sondern über einen fachlich definierten Datentyp beschrieben wird.

Die Taxonomie nutzt solche Strukturen, wenn ein Wert technisch kontrolliert, aber nicht auf eine kleine Enum-Liste beschränkt sein soll.

## Enumeration

Eine Enumeration ist eine kontrollierte Werteliste. Sie begrenzt die erlaubten Ausprägungen.

Im Projekt liegen die Enumerationsdefinitionen unter [all/enumerations/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/enumerations/).

## Label

Ein Label ist die lesbare Bezeichnung eines Konzepts. Labels machen technische Konzepte für Menschen verständlich.

Im Core-Teil werden Labels über [common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd) eingebunden.

## Reference

References verbinden ein technisches Konzept mit der fachlichen Quelle.

Die Hauptquelle ist [common/references/ref_esrs.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml). Dort zeigen viele Referenzen auf die ESRS-Quelle bei EFRAG.

## catalog / rewriteURI

Der XML-Katalog lenkt externe URIs auf lokale Dateien um. Das macht die Taxonomie offline nutzbar.

In [META-INF/catalog.xml](META-INF/catalog.xml) wird der Online-Pfad `https://xbrl.efrag.org/taxonomy/esrs/2023-12-22/` lokal auf den Repository-Baum umgeleitet.

## Interne Beziehung

Eine interne Beziehung verbindet Ressourcen innerhalb dieses Repositories.

Beispiele:

- [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd) bindet Linkbases ein.
- [common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd) bindet Labels, References, Dimensionen und Enumerationen ein.
- Linkbases verweisen auf Rollen und Strukturen.

## Externe Beziehung

Eine externe Beziehung zeigt auf Standards oder auf die offizielle fachliche Quelle.

Beispiele:

- `http://www.xbrl.org/2016/taxonomy-package.xsd`
- `http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd`
- `https://xbrl.efrag.org/e-esrs/esrs-set1-2023.html#...`

## Hierarchie

Hierarchie bedeutet in diesem Projekt vor allem die fachliche Baumstruktur in den Presentation Linkbases und die semantische Struktur in den Definition Linkbases.

Die Taxonomie ist also nicht nur eine Sammlung von Dateien, sondern ein graphartig verknüpftes Modell mit abgestuften Rollen, Konzepten und Beziehungen.

## Merksatz

Wenn du die Taxonomie verstehen willst, denke in vier Ebenen:

1. Paket und Entry Points in [META-INF/](META-INF/)
2. Fachschema in [esrs_all.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/esrs_all.xsd) und [common/esrs_cor.xsd](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/esrs_cor.xsd)
3. Beziehungsnetz in [all/linkbases/](xbrl.efrag.org/taxonomy/esrs/2023-12-22/all/linkbases/)
4. Fachliche Rückverfolgung über [common/references/ref_esrs.xml](xbrl.efrag.org/taxonomy/esrs/2023-12-22/common/references/ref_esrs.xml)
