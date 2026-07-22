package org.esrs.pipeline.xbrl.fact;

public record XbrlFact(String field,
                       String occurrenceKey,
                       String conceptQname,
                       String contextRef,
                       String unitRef,
                       String value,
                       String decimals,
                       boolean numeric,
                       boolean enumeration) {
}
