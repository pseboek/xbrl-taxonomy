package org.esrs.pipeline.mapping;

import java.util.List;
import org.esrs.pipeline.model.DimensionSelection;

public record MappingEntry(String field,
                           String concept,
                           String type,
                           String unit,
                           String period,
                           String enumerationDomain,
                           List<String> allowedValues,
                           Integer decimals,
                           List<DimensionSelection> dimensions) {
}
