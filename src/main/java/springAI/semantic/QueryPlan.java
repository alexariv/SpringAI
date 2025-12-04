package springAI.semantic;

public class QueryPlan {

    private String semanticQuery;
    private String filterExpression; // can be null

    public QueryPlan() {
    }

    public QueryPlan(String semanticQuery, String filterExpression) {
        this.semanticQuery = semanticQuery;
        this.filterExpression = filterExpression;
    }

    public String getSemanticQuery() {
        return semanticQuery;
    }

    public void setSemanticQuery(String semanticQuery) {
        this.semanticQuery = semanticQuery;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }
}
