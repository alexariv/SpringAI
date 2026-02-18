package springAI.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class QueryPlannerService {
    private static final Logger logger = LoggerFactory.getLogger(QueryPlannerService.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QueryPlannerService(ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public QueryPlan plan(String userQuery) {
        String systemPrompt = """
           You are a query parser for an operation log search system.
            1. Extract semantic concepts for text search (goes in "semanticQuery")
            2. Build a metadata filter expression ONLY if value is an allowed field from below.

            Metadata format and rules:
            - Use SQL-like syntax for filter expressions as a String.
            EQUALS: '=='; MINUS : '-'; PLUS: '+'; GT: '>'; GE: '>='; LT: '<'; LE: '<='; NE: '!=';
            AND: 'AND' | 'and' | '&&'; OR: 'OR' | 'or' | '||';
            IN: 'IN' | 'in'; NIN: 'NIN' | 'nin'; NOT: 'NOT' | 'not';
            IS: 'IS' | 'is'; NULL: 'NULL' | 'null'; NOT NULL: 'NOT NULL' | 'not null';
            -If a term sounds like it could be metadata but is NOT in the allowed lists, treat 
            it as semantic search content and do NOT include it in the filter expression and DO NOT 
            deviant from the approved list.

            The backend has:
            - A semantic search over the `description` text using embeddings.
            - Metadata fields available for filtering:
                - owner          
                - state          
                - level        
                - logbooks_name  
                - tags_name     

            logbooks_name options (case-sensitive):
            - "Acc Control Software"
            - "Controls Commissioning"
            - "Diagnositics"
            - "Electronics Maintenance"
            - "Fault Reports"
            - "gbassi"
            - "LOTO"
            - "Machine Physics"
            - "Mechanical Technicians"
            - "Operations"
            
            tags_name options (case-sensitive):
            - "Active Interlock"
            - "Alarm"
            - "ARMs"
            - "Authorization"
            - "Beam Available"
            - "Beam Dump"
            - "Beamline"
            - "Call In/Called"
            - "Checklists"
            - "Controls"
            - "Cryo"
            - "Diagnostics"
            - "EPS/PPS"
            - "Fault"
            - "Feedback"
            - "FLOCO"
            - "Injection"
            - "Interlock Tests"
            - "Maintenance"
            - "MASAR"
            - "Power Supplies"
            - "RCT"
            - "Reference"
            - "RF Systems"
            - "SoftIOC"
            - "Start Shift"
            - "Studies"
            - "Summary"
            - "Testing"
            - "Timely Order"
            - "Timing Systems"
            - "Utilities"
            - "Vacuum"
            - "Work Permits"

            level options: "Info", "Urgent", "Warning", "Error"
            state options: "Active", "Inactive"

             METADATA FILTERING STRATEGY:
            Tags are optional on entries, so be inclusive. Follow these rules IN ORDER:
            
            1. EXPLICIT TAG REQUEST (highest priority):
            - If query contains "tagged", "tag", or "with [tag name] tag"
            - USE BOTH logbook AND tag filters together
            - Examples: 
                * "tagged summary in Operations" → filter by BOTH
                * "Operations entries with Alarm tag" → filter by BOTH
            
            2. LOGBOOK + TAG CONCEPT (without explicit "tagged"):
            - Filter by logbook only
            - Put tag concept in semanticQuery
            - Examples:
                * "controls commissioning interlock testing" → logbook filter only
                * "Operations alarm events" → logbook filter only
            
            3. TAG ONLY (no logbook mentioned):
            - Use tag filter
            - Examples:
                * "show me alarm entries" → tag filter only
                * "maintenance logs" → tag filter only
            
            4. LOGBOOK ONLY:
            - Use logbook filter
            
            5. NEITHER:
            - No filter, semantic search only
            
            Output format:
            - Return ONLY a JSON object with the fields:
              {
                "semanticQuery": "<string>",
                "filterExpression": "<string or null>"
              }

            - If there are no metadata filters, set "filterExpression" to null.
            """;

        return executeWithRetry(() -> {
            String rawResponse = this.chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userQuery)
                    .call()
                    .content();

        String json = extractJson(rawResponse);

        try {
            QueryPlan plan = objectMapper.readValue(json, QueryPlan.class);
            logger.info("Parsed QueryPlan: semanticQuery: '{}', filterExpression: '{}'",
                plan.getSemanticQuery(), plan.getFilterExpression());
            return plan;
        }
        catch (Exception e) {
            logger.warn("JSON parsing failed, using fallback: {}", e.getMessage());
            // Fallback: if parsing fails
            QueryPlan fallback = new QueryPlan();
            fallback.setSemanticQuery(userQuery);
            fallback.setFilterExpression(null);
            return fallback;
        }
        }, userQuery);
    }
     private QueryPlan executeWithRetry(java.util.function.Supplier<QueryPlan> operation, String userQuery) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    long backoffTime = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
                    logger.info("Retrying in {}ms...", backoffTime);
                    try {
                        Thread.sleep(backoffTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted", ie);
                        break;
                    }
                }
            }
        }
        
        // All retries failed - return fallback
        logger.error("All {} retry attempts failed. Returning fallback for query: {}", 
                    MAX_RETRIES, userQuery, lastException);
        QueryPlan fallback = new QueryPlan();
        fallback.setSemanticQuery(userQuery);
        fallback.setFilterExpression(null);
        return fallback;
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }
}
