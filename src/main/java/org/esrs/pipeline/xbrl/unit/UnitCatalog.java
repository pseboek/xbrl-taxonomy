package org.esrs.pipeline.xbrl.unit;

import java.util.List;
import java.util.Map;

public final class UnitCatalog {
    public static final String NS_XBRLI = "http://www.xbrl.org/2003/instance";
    public static final String NS_ISO4217 = "http://www.xbrl.org/2003/iso4217";
    public static final String NS_UOM = "http://www.xbrl.org/2009/utr";

    private static final Map<String, UnitDefinition> DEFINITIONS = Map.ofEntries(
        Map.entry("EUR", UnitDefinition.simple("iso4217:EUR")),
        Map.entry("USD", UnitDefinition.simple("iso4217:USD")),
        Map.entry("kWh", UnitDefinition.simple("uom:kWh")),
        Map.entry("MWh", UnitDefinition.simple("uom:MWh")),
        Map.entry("GJ", UnitDefinition.simple("uom:GJ")),
        Map.entry("tCO2e", UnitDefinition.simple("uom:tCO2e")),
        Map.entry("kg", UnitDefinition.simple("uom:kg")),
        Map.entry("m3", UnitDefinition.simple("uom:m3")),
        Map.entry("count", UnitDefinition.simple("xbrli:pure")),
        Map.entry("shares", UnitDefinition.simple("xbrli:shares")),
        Map.entry("FTE", UnitDefinition.simple("uom:FTE")),
        Map.entry("percent", UnitDefinition.divide("xbrli:pure", "uom:percentItem")),
        Map.entry("ratio", UnitDefinition.simple("xbrli:pure")),
        Map.entry("tCO2e_per_EUR", UnitDefinition.divide("uom:tCO2e", "iso4217:EUR")),
        Map.entry("kWh_per_EUR", UnitDefinition.divide("uom:kWh", "iso4217:EUR")),
        Map.entry("m3_per_EUR", UnitDefinition.divide("uom:m3", "iso4217:EUR")),
        Map.entry("EUR_per_share", UnitDefinition.divide("iso4217:EUR", "xbrli:shares"))
    );

    private UnitCatalog() {
    }

    public static UnitDefinition resolve(String unitKey) {
        if (unitKey == null || unitKey.isBlank()) {
            return UnitDefinition.simple("xbrli:pure");
        }
        return DEFINITIONS.getOrDefault(unitKey, UnitDefinition.simple("xbrli:pure"));
    }

    public static String sanitizeUnitRef(String unitKey) {
        return "u_" + unitKey.replace(':', '_').replace('-', '_').replace('/', '_').replace('%', 'p');
    }

    public static record UnitDefinition(List<String> numeratorMeasures, List<String> denominatorMeasures) {
        public static UnitDefinition simple(String measure) {
            return new UnitDefinition(List.of(measure), List.of());
        }

        public static UnitDefinition divide(String numerator, String denominator) {
            return new UnitDefinition(List.of(numerator), List.of(denominator));
        }

        public boolean isDivide() {
            return !denominatorMeasures.isEmpty();
        }
    }
}
