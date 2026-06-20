package com.foggyframework.dataset.db.model.plugins.pipeline;

import java.util.List;

/**
 * Context contract for optional bounded pipeline loop hooks.
 */
public interface LoopablePipelineContext {

    int getLoopIndex();

    void setLoopIndex(int loopIndex);

    int getMaxLoopCount();

    boolean isLoopStopRequested();

    String getLoopStopReason();

    void requestLoopStop(String reason);

    void clearLoopStop();

    boolean isLoopChanged();

    void markLoopChanged();

    void clearLoopChanged();

    void addLoopTrace(String stepName, LoopDecision decision);

    List<LoopTraceEntry> getLoopTrace();
}
