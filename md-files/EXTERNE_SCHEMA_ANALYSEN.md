# Externe Schema-Analysen

Die Ansicht `output/taxonomy-visualization-external-schemas.html` dokumentiert die extern referenzierten XBRL- und XSD-Schemata. Die Tabellen sind clientseitig filterbar und ueber die Spaltenkoepfe sortierbar.

## Substitution Groups

Die Substitution-Group-Tabelle zeigt jedes XSD-Element mit `substitutionGroup`, seinem Typ, dem Zielnamespace und dem Abstrakt-Status. Dadurch werden unter anderem Elemente sichtbar, die `xbrldt:hypercubeItem` oder `xbrldt:dimensionItem` erweitern.

Die Gruppierung beantwortet insbesondere:

- Welche Elemente erweitern einen bestimmten XBRL-Standardbaustein?
- Welche ESRS-Tabellenkonzepte sind Hypercube-Elemente?
- Welche Elemente sind abstrakt und damit nicht direkt instanziierbar?

Die Daten stammen aus den geladenen externen XSDs und den lokalen ESRS-Taxonomie-XSDs.

## Import-/Include-Matrix

Die Matrix stellt die gefundenen `xsd:import`- und `xsd:include`-Beziehungen gegenueber. Die Zeilen sind die Quellschemata, die Spalten die Zielnamespaces oder SchemaLocations. Eine Zelle kann `import`, `include` oder beide Beziehungen enthalten.

Damit lassen sich zentrale Abhaengigkeiten, isolierte Schemas und auffaellige Include-Ketten schnell erkennen.

## Typkategorie- und Basistyp-Ranking

Die Rankings aggregieren das Typinventar nach Kategorie und Basistyp. Neben der absoluten Anzahl wird der Anteil am gesamten Inventar angezeigt. Die Tabellen koennen ueber die Spaltenkoepfe sortiert werden.

Das Kategorie-Ranking zeigt beispielsweise den Anteil von Enumerationen, komplexen Typen oder Elementen. Das Basistyp-Ranking macht sichtbar, welche XSD- oder XBRL-Basistypen die externen Definitionen dominieren.

## Facet-/Enumeration-Analyse

Die Facet-Tabelle zaehlt, wie oft Constraints wie `length`, `pattern`, `minInclusive` oder `enumeration` in den Typdefinitionen vorkommen. Die Enumeration-Tabelle listet die Typen mit ihrer Anzahl an erlaubten Enumerationswerten, absteigend nach Groesse.

Damit werden stark eingeschraenkte Typen und besonders grosse Wertelisten sichtbar, ohne die einzelnen XSD-Dateien manuell durchsuchen zu muessen.