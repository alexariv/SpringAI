package springAI.semantic;

import java.util.List;

// requests for LLM analysis from simple search results and original query
public class AnalysisRequest {

    private String query;
    private List<SearchHitDto> hits;

    public AnalysisRequest() {
    }

    public AnalysisRequest(String query, List<SearchHitDto> hits) {
        this.query = query;
        this.hits = hits;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<SearchHitDto> getHits() {
        return hits;
    }

    public void setHits(List<SearchHitDto> hits) {
        this.hits = hits;
    }
}