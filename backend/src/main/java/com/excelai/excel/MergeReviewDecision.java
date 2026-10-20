package com.excelai.excel;

import java.util.Map;
import java.util.Objects;

public final class MergeReviewDecision {
    private MergeReviewDecision() { }

    public enum Action { CORRECT, KEEP, SKIP }

    public record SourceRowKey(Long fileId, String sheetName, int rowNumber) {
        public SourceRowKey {
            Objects.requireNonNull(fileId);
            Objects.requireNonNull(sheetName);
            if (rowNumber < 2) throw new IllegalArgumentException("Source row number must be at least 2");
        }
    }

    public record Decision(Action action, Map<String, String> corrections) {
        public Decision {
            Objects.requireNonNull(action);
            corrections = corrections == null ? Map.of() : Map.copyOf(corrections);
        }
    }
}
