# Externe Schema-Analysen

Die Ansicht `output/taxonomy-visualization-external-schemas.html` dokumentiert die extern referenzierten XBRL- und XSD-Schemata. Die Tabellen sind clientseitig filterbar und ueber die Spaltenkoepfe sortierbar.

## Substitution Groups

Die Substitution-Group-Tabelle zeigt jedes XSD-Element mit `substitutionGroup`, seinem Typ, dem Zielnamespace und dem Abstrakt-Status. Dadurch werden unter anderem Elemente sichtbar, die `xbrldt:hypercubeItem` oder `xbrldt:dimensionItem` erweitern.

Die Gruppierung beantwortet insbesondere:

- Welche Elemente erweitern einen bestimmten XBRL-Standardbaustein?
- Welche ESRS-Tabellenkonzepte sind Hypercube-Elemente?
- Welche Elemente sind abstrakt und damit nicht direkt instanziierbar?

Die Daten stammen aus den geladenen externen XSDs und den lokalen ESRS-Taxonomie-XSDs.