package springAI.semantic;

import java.util.List;

public class SemanticSearchResponse {

    private List<SearchHitDto> hits;
    private String analysis;

    public SemanticSearchResponse() {
    }

    public SemanticSearchResponse(List<SearchHitDto> hits, String analysis) {
        this.hits = hits;
        this.analysis = analysis;
    }

    public List<SearchHitDto> getHits() {
        return hits;
    }

    public void setHits(List<SearchHitDto> hits) {
        this.hits = hits;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
}
