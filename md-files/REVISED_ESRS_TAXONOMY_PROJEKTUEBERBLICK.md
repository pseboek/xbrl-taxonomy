# Revised ESRS Draft XBRL Taxonomy

## Projektueberblick

Dieses Dokument beschreibt die Revised ESRS Draft XBRL Taxonomy von EFRAG. Sie wird im Projekt parallel zur bisherigen ESRS Set 1 XBRL Taxonomy weitergefuehrt und ersetzt die alte Taxonomie nicht.

Die neue Spezifikation basiert laut `META-INF/taxonomyPackage.xml` auf den ueberarbeiteten European Sustainability Reporting Standards, die am 3. Juli 2026 als Delegated Act veroeffentlicht wurden. Das Taxonomy Package traegt die Version `2026-08-30` und wurde am 17. September 2026 publiziert.

Der eigentliche Taxonomiebaum liegt unter:

`specifications/Revised-ESRS-Draft-XBRL-Taxonomy-September-2026/Revised-ESRS-Draft-XBRL-Taxonomy-September-2026/xbrl.efrag.org/taxonomy/esrs/2026-08-30/`

## Paketstruktur

Das neue Paket ist wesentlich kompakter als die bisherige Taxonomie. Es enthaelt einen aktiven Entry Point und konsolidierte Linkbases:

- `META-INF/taxonomyPackage.xml`: Paketname, Version, Publisher, Publikationsdatum und Entry Point
- `META-INF/catalog.xml`: lokale URI-Aufloesung fuer das Taxonomy Package
- `xbrl.efrag.org/taxonomy/esrs/2026-08-30/esrs.xsd`: aktiver Entry Point mit Konzepten, RoleTypes und Linkbase-Referenzen
- `esrs-presentation.xml`: Presentation-Linkbase
- `esrs-definition.xml`: Definitionen, Dimensionen, Domains und Members
- `esrs-calculation.xml`: Calculation-Linkbase
- `esrs-reference.xml`: Referenzen zur Spezifikation
- `label-en.xml`: englische Labels

Im Unterschied zur alten Taxonomie gibt es keine getrennten `all/`- und `common/`-Bereiche, keine Vielzahl thematischer `pre_`-/`def_`-/`cal_`-Dateien und nach dem aktuellen Paketbestand keine separaten Formula-Linkbases.

## Technische Eckdaten

- Taxonomie-Namespace: `https://xbrl.efrag.org/taxonomy/esrs/2026-08-30`
- Entry Point: `esrs.xsd`
- DTR-Typen: `http://www.xbrl.org/dtr/type/2024-01-31`
- Extensible Enumerations 2.0: `http://xbrl.org/2020/extensible-enumerations-2.0`
- ISO-3166-Import fuer Laendercodes
- NACE-Taxonomie `2026-05-01`
- RoleTypes direkt in `esrs.xsd`, unter anderem mit Rollen wie `role-100100` und `role-101200`

Die neue Taxonomie umfasst deutlich weniger XSD-Elemente als die alte Fassung, ist aber fachlich nicht nur neu verpackt. Die Konzeptnamen und viele fachliche Strukturen wurden neu geordnet. Deshalb kann das alte Mapping nicht automatisch als Mapping fuer die neue Version verwendet werden.

## Vergleich zur bisherigen Taxonomie

| Bereich | Bisherige ESRS Set 1 Taxonomy | Revised ESRS Draft |
| --- | --- | --- |
| Version | `2023-12-22` | `2026-08-30` |
| Publikation | 30.08.2024 | 17.09.2026 |
| Entry Points | `esrs_all.xsd` und `common/esrs_cor.xsd` | `esrs.xsd` |
| Paketstruktur | stark modularisiert | konsolidierte Dateien |
| XML/XSD-Bestand | 320 XML plus 2 XSD | 7 XML plus 1 XSD |
| XSD-Elemente | 5.430 | 1.058 |
| Dimensionen | 99 | 45 |
| Hypercubes | 209 | 60 |
| Formula-Linkbases | 4 separate Dateien | im Paketbestand nicht vorhanden |

Die alte Spezifikation bleibt unter `specifications/ESRS Set 1 XBRL Taxonomy/` erhalten. Ihre bisherigen Mappings, Tests und Ausgabepfade werden nicht entfernt.

## Verwendung im Projekt

Der geplante Parallelbetrieb verwendet fuer jede Taxonomie eine eigene Konfiguration:

- eigene Taxonomie- und Entry-Point-Auswahl
- eigenes Mapping mit den passenden Konzept-QNames
- eigener Mapping-Scope
- versionspassender `schemaRef`
- getrenntes Output-Verzeichnis, zum Beispiel `output/2023-12-22/` und `output/2026-08-30/`

Dadurch koennen alte Berichte weiterhin reproduziert und neue Berichte beziehungsweise Analysen separat gegen die Revised-Taxonomie erzeugt werden.

## Visualisierungen

Die strukturellen Visualisierungen koennen auch fuer die Revised-Taxonomie erzeugt werden, sobald der Loader die konsolidierten Linkbases verarbeitet. Besonders sinnvoll sind:

- Presentation Tree, Graph, Layer und Stats
- Matrix, Flow, Allocation und Scope & Period
- Hypercube-, Dimensions- und Intersection-Ansichten
- Enumeration-, Reference- und External-Schema-Ansichten
- Coverage, Mapping Flow, Traceability und Impact-Analysen

Eingeschraenkt sind Formula-bezogene Ansichten. Dazu gehoeren Validation und Rule Coverage Matrix sowie Formula-Anteile in Calculation, Complexity und Concept Backlog. Diese koennen fuer die neue Taxonomie nur belastbar erzeugt werden, wenn entsprechende Formula-Linkbases bereitgestellt oder separat integriert werden. Andernfalls muessen sie als nicht vorhanden oder eingeschraenkt gekennzeichnet werden.

## Arelle

Im Projekt ist derzeit Arelle **2.42.1** enthalten. Fuer die neue Taxonomie soll Arelle **2.45.1** als Zielversion gegen einen identischen Testfall geprueft werden. Der Versionssprung umfasst laut Upstream-Vergleich zahlreiche Aenderungen, unter anderem bei XSD-Conformance, iXBRL-/IXDS-Ladevorgaengen und internen Datentypbehandlungen.

Die alte Arelle-Version bleibt fuer Regressionstests der bisherigen Taxonomie erhalten. Ein Wechsel der Standardversion erfolgt erst nach einem direkten Vergleich beider Versionen gegen die neue `esrs.xsd`, insbesondere mit Blick auf:

- enum2 und DTR 2024-01-31
- ISO-3166- und NACE-Aufloesung
- Dimensions- und Linkbase-Verarbeitung
- XML-Katalog und lokale URI-Aufloesung
- XBRL- und iXBRL-Validierung

## Status

Die Spezifikation ist im Repository abgelegt und XML-/XSD-seitig wohlgeformt. Die Java-Pipeline, das Mapping und die Visualisierungs-Loader muessen fuer den Parallelbetrieb noch versionsbewusst auf die neue Paketstruktur ausgerichtet werden.

Verwandte Dokumentation:

- [Projekt-README](../README.md)
- [Projektstruktur](PROJEKTSTRUKTUR.md)
- [Technische Grundlagen](TECHNISCHE_GRUNDLAGEN.md)
