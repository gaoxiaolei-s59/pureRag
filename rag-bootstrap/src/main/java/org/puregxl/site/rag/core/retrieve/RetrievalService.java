package org.puregxl.site.rag.core.retrieve;

import org.puregxl.site.rag.core.intent.SubQuestionIntent;

import java.util.List;

public interface RetrievalService {
    RetrievalContext retrieval(List<SubQuestionIntent> subIntents, int defaultTopK);
}
