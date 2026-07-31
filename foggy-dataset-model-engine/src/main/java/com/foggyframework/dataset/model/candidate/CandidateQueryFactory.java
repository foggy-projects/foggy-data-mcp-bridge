package com.foggyframework.dataset.model.candidate;

/** Internal engine port for opening governed candidate-query sessions. */
public interface CandidateQueryFactory {

    CandidateQuerySession open(CandidateQuerySource source);
}
