package org.esrs.pipeline.xbrl.serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.esrs.pipeline.model.ReportEnvelope;
import org.esrs.pipeline.xbrl.context.ContextKey;
import org.esrs.pipeline.xbrl.fact.FactBuilder;
import org.esrs.pipeline.xbrl.fact.XbrlFact;
import org.esrs.pipeline.xbrl.unit.UnitCatalog;
import org.esrs.pipeline.xbrl.unit.UnitCatalog.UnitDefinition;

public class XbrlInstanceWriter {
    private static final String NS_XBRLI = UnitCatalog.NS_XBRLI;
    private static final String NS_LINK = "http://www.xbrl.org/2003/linkbase";
    private static final String NS_XLINK = "http://www.w3.org/1999/xlink";
    private static final String NS_XBRLDI = "http://xbrl.org/2006/xbrldi";
    private static final String NS_ESRS = "https://xbrl.efrag.org/taxonomy/esrs/2023-12-22";
    private static final String NS_ISO4217 = UnitCatalog.NS_ISO4217;
    private static final String NS_UOM = UnitCatalog.NS_UOM;
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    public void write(Path outputFile,
                      ReportEnvelope envelope,
                      Map<ContextKey, String> contexts,
                      List<XbrlFact> facts,
                      String schemaRefHref) throws IOException {
        Files.createDirectories(outputFile.getParent());

        Map<String, UnitDefinition> units = collectUnits(facts);

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(outputStream, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            writer.setPrefix("xbrli", NS_XBRLI);
            writer.setPrefix("link", NS_LINK);
            writer.setPrefix("xlink", NS_XLINK);
            writer.setPrefix("xbrldi", NS_XBRLDI);
            writer.setPrefix("esrs", NS_ESRS);
            writer.setPrefix("iso4217", NS_ISO4217);
            writer.setPrefix("uom", NS_UOM);
            writer.setPrefix("xsi", NS_XSI);

            writer.writeStartElement("xbrli", "xbrl", NS_XBRLI);
            writer.writeNamespace("xbrli", NS_XBRLI);
            writer.writeNamespace("link", NS_LINK);
            writer.writeNamespace("xlink", NS_XLINK);
            writer.writeNamespace("xbrldi", NS_XBRLDI);
            writer.writeNamespace("esrs", NS_ESRS);
            writer.writeNamespace("iso4217", NS_ISO4217);
            writer.writeNamespace("uom", NS_UOM);
            writer.writeNamespace("xsi", NS_XSI);

            writeSchemaRef(writer, schemaRefHref);
            writeContexts(writer, contexts);
            writeUnits(writer, units);
            writeFacts(writer, facts);

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write XBRL instance", e);
        }
    }

    private void writeSchemaRef(XMLStreamWriter writer, String schemaRefHref) throws XMLStreamException {
        writer.writeCharacters("\n  ");
        writer.writeEmptyElement("link", "schemaRef", NS_LINK);
        writer.writeAttribute("xlink", NS_XLINK, "type", "simple");
        writer.writeAttribute("xlink", NS_XLINK, "href", schemaRefHref);
    }

    private void writeContexts(XMLStreamWriter writer, Map<ContextKey, String> contexts) throws XMLStreamException {
        for (Map.Entry<ContextKey, String> e : contexts.entrySet()) {
            ContextKey key = e.getKey();
            String id = e.getValue();

            writer.writeCharacters("\n  ");
            writer.writeStartElement("xbrli", "context", NS_XBRLI);
            writer.writeAttribute("id", id);

            writer.writeCharacters("\n    ");
            writer.writeStartElement("xbrli", "entity", NS_XBRLI);
            writer.writeCharacters("\n      ");
            writer.writeStartElement("xbrli", "identifier", NS_XBRLI);
            writer.writeAttribute("scheme", key.entityScheme());
            writer.writeCharacters(key.entityIdentifier());
            writer.writeEndElement();
            writer.writeCharacters("\n    ");
            writer.writeEndElement();

            writer.writeCharacters("\n    ");
            writer.writeStartElement("xbrli", "period", NS_XBRLI);
            if (key.instant()) {
                writer.writeCharacters("\n      ");
                writer.writeStartElement("xbrli", "instant", NS_XBRLI);
                writer.writeCharacters(key.endDate().toString());
                writer.writeEndElement();
            } else {
                writer.writeCharacters("\n      ");
                writer.writeStartElement("xbrli", "startDate", NS_XBRLI);
                writer.writeCharacters(key.startDate().toString());
                writer.writeEndElement();
                writer.writeCharacters("\n      ");
                writer.writeStartElement("xbrli", "endDate", NS_XBRLI);
                writer.writeCharacters(key.endDate().toString());
                writer.writeEndElement();
            }
            writer.writeCharacters("\n    ");
            writer.writeEndElement();

            if (!key.dimensions().isEmpty()) {
                writer.writeCharacters("\n    ");
                writer.writeStartElement("xbrli", "scenario", NS_XBRLI);
                for (Map.Entry<String, String> d : key.dimensions().entrySet()) {
                    writer.writeCharacters("\n      ");
                    writer.writeStartElement("xbrldi", "explicitMember", NS_XBRLDI);
                    writer.writeAttribute("dimension", d.getKey());
                    writer.writeCharacters(d.getValue());
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n    ");
                writer.writeEndElement();
            }

            writer.writeCharacters("\n  ");
            writer.writeEndElement();
        }
    }

    private void writeUnits(XMLStreamWriter writer, Map<String, UnitDefinition> units) throws XMLStreamException {
        for (Map.Entry<String, UnitDefinition> e : units.entrySet()) {
            UnitDefinition definition = e.getValue();
            writer.writeCharacters("\n  ");
            writer.writeStartElement("xbrli", "unit", NS_XBRLI);
            writer.writeAttribute("id", e.getKey());
            if (definition.isDivide()) {
                writer.writeCharacters("\n    ");
                writer.writeStartElement("xbrli", "divide", NS_XBRLI);
                writer.writeCharacters("\n      ");
                writer.writeStartElement("xbrli", "unitNumerator", NS_XBRLI);
                for (String measure : definition.numeratorMeasures()) {
                    writer.writeCharacters("\n        ");
                    writer.writeStartElement("xbrli", "measure", NS_XBRLI);
                    writer.writeCharacters(measure);
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n      ");
                writer.writeEndElement();
                writer.writeCharacters("\n      ");
                writer.writeStartElement("xbrli", "unitDenominator", NS_XBRLI);
                for (String measure : definition.denominatorMeasures()) {
                    writer.writeCharacters("\n        ");
                    writer.writeStartElement("xbrli", "measure", NS_XBRLI);
                    writer.writeCharacters(measure);
                    writer.writeEndElement();
                }
                writer.writeCharacters("\n      ");
                writer.writeEndElement();
                writer.writeCharacters("\n    ");
                writer.writeEndElement();
            } else {
                for (String measure : definition.numeratorMeasures()) {
                    writer.writeCharacters("\n    ");
                    writer.writeStartElement("xbrli", "measure", NS_XBRLI);
                    writer.writeCharacters(measure);
                    writer.writeEndElement();
                }
            }
            writer.writeCharacters("\n  ");
            writer.writeEndElement();
        }
    }

    private void writeFacts(XMLStreamWriter writer, List<XbrlFact> facts) throws XMLStreamException {
        for (XbrlFact fact : facts) {
            String localName = fact.conceptQname().substring(fact.conceptQname().indexOf(':') + 1);
            writer.writeCharacters("\n  ");
            writer.writeStartElement("esrs", localName, NS_ESRS);
            writer.writeAttribute("contextRef", fact.contextRef());
            boolean nilFact = FactBuilder.NIL_SENTINEL.equals(fact.value());
            if (nilFact) {
                writer.writeAttribute("xsi", NS_XSI, "nil", "true");
            }
            if (!nilFact && fact.unitRef() != null) {
                writer.writeAttribute("unitRef", fact.unitRef());
            }
            if (!nilFact && fact.decimals() != null) {
                writer.writeAttribute("decimals", fact.decimals());
            }
            if (!nilFact) {
                writer.writeCharacters(fact.value());
            }
            writer.writeEndElement();
        }
        writer.writeCharacters("\n");
    }

    private Map<String, UnitDefinition> collectUnits(List<XbrlFact> facts) {
        Map<String, UnitDefinition> unitMap = new LinkedHashMap<>();
        for (XbrlFact fact : facts) {
            if (fact.unitRef() == null) {
                continue;
            }
            String unitKey = fact.unitRef().startsWith("u_") ? fact.unitRef().substring(2) : fact.unitRef();
            unitMap.putIfAbsent(fact.unitRef(), UnitCatalog.resolve(unitKey));
        }
        return unitMap;
    }
}
