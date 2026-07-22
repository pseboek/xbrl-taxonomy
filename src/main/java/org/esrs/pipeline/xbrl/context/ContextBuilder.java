package org.esrs.pipeline.xbrl.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.esrs.pipeline.model.DimensionSelection;
import org.esrs.pipeline.model.DisclosureFact;
import org.esrs.pipeline.model.ReportEnvelope;

public class ContextBuilder {

    public ContextBuildResult build(ReportEnvelope envelope) {
        Map<ContextKey, String> contextIds = new LinkedHashMap<>();
        Map<String, String> fieldContextRef = new LinkedHashMap<>();

        int seq = 1;
        for (DisclosureFact fact : envelope.facts()) {
            ContextKey key = buildKey(envelope, fact.dimensions());
            String contextId = contextIds.computeIfAbsent(key, k -> "c" + contextIds.size() + 1);
            fieldContextRef.put(fact.field() + "#" + seq, contextId);
            seq++;
        }

        return new ContextBuildResult(contextIds, fieldContextRef);
    }

    private ContextKey buildKey(ReportEnvelope envelope, List<DimensionSelection> dimensions) {
        Map<String, String> dims = new LinkedHashMap<>();
        if (dimensions != null) {
            for (DimensionSelection d : dimensions) {
                dims.put(d.axisQname(), d.memberQname());
            }
        }
        return new ContextKey(
            envelope.entity().scheme(),
            envelope.entity().identifier(),
            envelope.period().startDate(),
            envelope.period().endDate(),
            envelope.period().instant(),
            dims
        );
    }

    public record ContextBuildResult(Map<ContextKey, String> contexts, Map<String, String> fieldOccurrenceContext) {
    }
}
