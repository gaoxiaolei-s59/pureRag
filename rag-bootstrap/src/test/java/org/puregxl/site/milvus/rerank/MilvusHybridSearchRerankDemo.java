package org.puregxl.site.milvus.rerank;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.response.SearchResp;
import org.puregxl.site.milvus.hybrid.MilvusHybridSchemaDemo2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MilvusHybridSearchRerankDemo {

    public static void main(String[] args) throws IOException {
        String query = "缓考申请需要什么材料";

        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://localhost:19530")
                .build());

        MilvusHybridSchemaDemo2.createCollectionIfAbsentAndLoad(client);

        MilvusHybridSchemaDemo2.SearchConfig cfg = MilvusHybridSchemaDemo2.SearchConfig.defaults();
        cfg.finalTopK = 6;
        cfg.denseRecallTopK = 20;
        cfg.sparseRecallTopK = 20;

        SearchResp searchResp = MilvusHybridSchemaDemo2.runSearch(
                client,
                query,
                MilvusHybridSchemaDemo2.SearchMode.HYBRID,
                cfg
        );

        List<Candidate> candidates = extractCandidates(searchResp);
        printSearchCandidates(candidates);
        if (candidates.isEmpty()) {
            System.out.println("没有搜索候选结果，跳过 rerank。");
            return;
        }

        List<String> candidateTexts = candidates.stream()
                .map(candidate -> candidate.text)
                .toList();

        List<SiliconFlowRerankDemo.RerankItem> rerankResults = SiliconFlowRerankDemo.rerank(
                query,
                candidateTexts,
                Math.min(3, candidateTexts.size())
        );

        printRerankResults(rerankResults, candidates);
    }

    private static List<Candidate> extractCandidates(SearchResp searchResp) {
        List<Candidate> candidates = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return candidates;
        }

        List<SearchResp.SearchResult> oneQueryResults = searchResults.get(0);
        for (int i = 0; i < oneQueryResults.size(); i++) {
            SearchResp.SearchResult result = oneQueryResults.get(i);
            Map<String, Object> entity = result.getEntity();
            Object fileId = entity == null ? null : entity.get("file_id");
            Object text = entity == null ? null : entity.get("text");
            if (text == null) {
                continue;
            }
            candidates.add(new Candidate(i + 1, result.getScore(), String.valueOf(fileId), String.valueOf(text)));
        }


        
        return candidates;
    }

    private static void printSearchCandidates(List<Candidate> candidates) {
        System.out.println("\n===== Hybrid Search Candidates =====");
        for (Candidate candidate : candidates) {
            System.out.println("Search-Top-" + candidate.searchRank + " score=" + candidate.searchScore);
            System.out.println("  file_id: " + candidate.fileId);
            System.out.println("  " + candidate.text);
        }
    }

    private static void printRerankResults(List<SiliconFlowRerankDemo.RerankItem> rerankResults,
                                           List<Candidate> candidates) {
        System.out.println("\n===== Rerank Results =====");
        for (int i = 0; i < rerankResults.size(); i++) {
            SiliconFlowRerankDemo.RerankItem item = rerankResults.get(i);
            Candidate candidate = candidates.get(item.index);
            System.out.println("Rerank-Top-" + (i + 1)
                    + " rerankScore=" + item.score
                    + ", searchRank=" + candidate.searchRank
                    + ", searchScore=" + candidate.searchScore);
            System.out.println("  file_id: " + candidate.fileId);
            System.out.println("  " + item.text);
        }
    }

    private record Candidate(int searchRank, double searchScore, String fileId, String text) {
    }
}
