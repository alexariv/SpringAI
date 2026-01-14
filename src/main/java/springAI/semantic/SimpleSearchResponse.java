package springAI.semantic;

import java.util.List;

// response for simple search- returns only hits without LLM analysis
public class SimpleSearchResponse {

    private List<SearchHitDto> hits;

    public SimpleSearchResponse() {
    }

    public SimpleSearchResponse(List<SearchHitDto> hits) {
        this.hits = hits;
    }

    public List<SearchHitDto> getHits() {
        return hits;
    }

    public void setHits(List<SearchHitDto> hits) {
        this.hits = hits;
    }
}