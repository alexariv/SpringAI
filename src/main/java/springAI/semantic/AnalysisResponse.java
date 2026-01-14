package springAI.semantic;

// response for LLM analysis of search results
public class AnalysisResponse {

    private String analysis;

    public AnalysisResponse() {
    }

    public AnalysisResponse(String analysis) {
        this.analysis = analysis;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
}
