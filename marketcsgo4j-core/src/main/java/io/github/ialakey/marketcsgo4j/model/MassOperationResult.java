package io.github.ialakey.marketcsgo4j.model;

import java.util.List;

/** The per-item outcome of a batch listing or repricing. */
public record MassOperationResult(List<MassOperationItem> items) {

    /** The maximum a single mass request may carry, as the market documents it. */
    public static final int MAX_ITEMS_PER_REQUEST = 50;

    public MassOperationResult {
        items = List.copyOf(items);
    }

    public List<MassOperationItem> succeeded() {
        return items.stream().filter(MassOperationItem::isSuccessful).toList();
    }

    public List<MassOperationItem> failed() {
        return items.stream().filter(item -> !item.isSuccessful()).toList();
    }

    /** Whether every item in the batch went through. */
    public boolean isCompletelySuccessful() {
        return failed().isEmpty();
    }
}
