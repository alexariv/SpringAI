package springAI.semantic;

public class SearchQueryRequest {

    private String query;

    // optional time range
    private Long fromEpochMs;   // inclusive lower bound
    private Long toEpochMs;     // inclusive upper bound

    public String getQuery() {
        return query;
    }
    public void setQuery(String query) {
        this.query = query;
    }

    public Long getFromEpochMs() {
        return fromEpochMs;
    }
    public void setFromEpochMs(Long fromEpochMs) {
        this.fromEpochMs = fromEpochMs;
    }

    public Long getToEpochMs() {
        return toEpochMs;
    }
    public void setToEpochMs(Long toEpochMs) {
        this.toEpochMs = toEpochMs;
    }
}
