package org.esrs.pipeline.model;

import java.util.List;

public record DisclosureFact(String field,
                             String value,
                             List<DimensionSelection> dimensions,
                             String unitOverride,
                             Integer decimalsOverride) {
}
