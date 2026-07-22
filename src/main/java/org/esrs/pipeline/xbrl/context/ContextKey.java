package org.esrs.pipeline.xbrl.context;

import java.time.LocalDate;
import java.util.Map;

public record ContextKey(String entityScheme,
                         String entityIdentifier,
                         LocalDate startDate,
                         LocalDate endDate,
                         boolean instant,
                         Map<String, String> dimensions) {
}
