package org.esrs.pipeline.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.esrs.pipeline.mapping.MappingEntry;
import org.esrs.pipeline.mapping.MappingRegistry;
import org.esrs.pipeline.model.DimensionSelection;

public final class TestTaxonomyFixture {
    private TestTaxonomyFixture() {
    }

    public static Path createMinimalFixture(Path fixtureRoot, Path mappingPath) throws IOException {
        Path taxonomyBase = fixtureRoot
            .resolve("xbrl.efrag.org")
            .resolve("taxonomy")
            .resolve("esrs")
            .resolve("2023-12-22");

        Path commonDir = taxonomyBase.resolve("common");
        Path linkbaseDir = taxonomyBase.resolve("all").resolve("linkbases");

        Files.createDirectories(commonDir);
        Files.createDirectories(linkbaseDir);

        MappingRegistry mappingRegistry = MappingRegistry.fromPath(mappingPath);
        Set<String> qnames = collectQnames(mappingRegistry);

        Files.writeString(commonDir.resolve("esrs_cor.xsd"), buildCoreSchema(qnames), StandardCharsets.UTF_8);
        Files.writeString(taxonomyBase.resolve("esrs_all.xsd"), buildAllSchemaStub(), StandardCharsets.UTF_8);

        Files.writeString(linkbaseDir.resolve("pre_esrs_minimal.xml"), minimalPresentationLinkbase(), StandardCharsets.UTF_8);
        Files.writeString(linkbaseDir.resolve("dim_esrs_minimal.xml"), minimalDimensionalLinkbase(), StandardCharsets.UTF_8);

        return fixtureRoot;
    }

    private static Set<String> collectQnames(MappingRegistry mappingRegistry) {
        Set<String> qnames = new TreeSet<>();
        for (MappingEntry entry : mappingRegistry.all().values()) {
            addQname(qnames, entry.concept());
            if (entry.dimensions() != null) {
                for (DimensionSelection dimension : entry.dimensions()) {
                    addQname(qnames, dimension.axisQname());
                    addQname(qnames, dimension.memberQname());
                }
            }
        }

        // Ensure minimal concepts used by synthetic linkbases exist.
        qnames.add("esrs:RootConcept");
        qnames.add("esrs:ChildConcept");
        qnames.add("esrs:PrimaryItem");
        qnames.add("esrs:HypercubeA");
        qnames.add("esrs:DimensionA");
        qnames.add("esrs:DomainA");
        qnames.add("esrs:MemberA");

        return qnames;
    }

    private static void addQname(Set<String> qnames, String value) {
        if (value == null || value.isBlank() || !value.startsWith("esrs:")) {
            return;
        }
        qnames.add(value);
    }

    private static String buildCoreSchema(Set<String> qnames) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" ")
            .append("xmlns:esrs=\"http://www.efrag.org/esrs\" ")
            .append("targetNamespace=\"http://www.efrag.org/esrs\" ")
            .append("elementFormDefault=\"qualified\">\n");

        for (String qname : qnames) {
            String localName = qname.substring("esrs:".length());
            builder.append("  <xsd:element name=\"")
                .append(localName)
                .append("\" abstract=\"false\"/>\n");
        }

        builder.append("</xsd:schema>\n");
        return builder.toString();
    }

    private static String buildAllSchemaStub() {
        return """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"
                        targetNamespace=\"http://www.efrag.org/esrs/all\"
                        elementFormDefault=\"qualified\">
            </xsd:schema>
            """;
    }

    private static String minimalPresentationLinkbase() {
        return """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <link:linkbase xmlns:link=\"http://www.xbrl.org/2003/linkbase\"
                           xmlns:xlink=\"http://www.w3.org/1999/xlink\">
              <link:presentationLink xlink:type=\"extended\" xlink:role=\"urn:esrs:test:role:presentation\">
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_root\" xlink:href=\"esrs_cor.xsd#RootConcept\"/>
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_child\" xlink:href=\"esrs_cor.xsd#ChildConcept\"/>
                <link:presentationArc xlink:type=\"arc\"
                                      xlink:arcrole=\"http://www.xbrl.org/2003/arcrole/parent-child\"
                                      xlink:from=\"loc_root\"
                                      xlink:to=\"loc_child\"
                                      order=\"1\"/>
              </link:presentationLink>
            </link:linkbase>
            """;
    }

    private static String minimalDimensionalLinkbase() {
        return """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <link:linkbase xmlns:link=\"http://www.xbrl.org/2003/linkbase\"
                           xmlns:xlink=\"http://www.w3.org/1999/xlink\">
              <link:definitionLink xlink:type=\"extended\" xlink:role=\"urn:esrs:test:role:dimension\">
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_primary\" xlink:href=\"esrs_cor.xsd#PrimaryItem\"/>
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_cube\" xlink:href=\"esrs_cor.xsd#HypercubeA\"/>
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_dim\" xlink:href=\"esrs_cor.xsd#DimensionA\"/>
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_domain\" xlink:href=\"esrs_cor.xsd#DomainA\"/>
                <link:loc xlink:type=\"locator\" xlink:label=\"loc_member\" xlink:href=\"esrs_cor.xsd#MemberA\"/>

                <link:definitionArc xlink:type=\"arc\"
                                    xlink:arcrole=\"http://xbrl.org/int/dim/arcrole/all\"
                                    xlink:from=\"loc_primary\"
                                    xlink:to=\"loc_cube\"
                                    order=\"1\"/>
                <link:definitionArc xlink:type=\"arc\"
                                    xlink:arcrole=\"http://xbrl.org/int/dim/arcrole/hypercube-dimension\"
                                    xlink:from=\"loc_cube\"
                                    xlink:to=\"loc_dim\"
                                    order=\"2\"/>
                <link:definitionArc xlink:type=\"arc\"
                                    xlink:arcrole=\"http://xbrl.org/int/dim/arcrole/dimension-domain\"
                                    xlink:from=\"loc_dim\"
                                    xlink:to=\"loc_domain\"
                                    order=\"3\"/>
                <link:definitionArc xlink:type=\"arc\"
                                    xlink:arcrole=\"http://xbrl.org/int/dim/arcrole/domain-member\"
                                    xlink:from=\"loc_domain\"
                                    xlink:to=\"loc_member\"
                                    order=\"4\"/>
              </link:definitionLink>
            </link:linkbase>
            """;
    }
}
