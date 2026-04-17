package springAI.semantic;
import java.util.List;

// response for LLM analysis of search results
public class AnalysisResponse {

    private String analysis;
    private List<SearchHitDto> hits;

    public AnalysisResponse() {
    }

    public AnalysisResponse(String analysis, List<SearchHitDto> hits) {
        this.analysis = analysis;
        this.hits = hits;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public List<SearchHitDto> getHits() { 
        return hits; 
    }

    public void setHits(List<SearchHitDto> hits) { 
        this.hits = hits; 
    }
}
