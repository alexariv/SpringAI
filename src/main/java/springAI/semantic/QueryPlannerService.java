package springAI.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class QueryPlannerService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QueryPlannerService(ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public QueryPlan plan(String userQuery) {
        String systemPrompt = """
            1. Extract a short semantic query for the description text.
            2. Build a metadata filter expression using ONLY the allowed fields above.

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

            Output format:
            - Return ONLY a JSON object with the fields:
              {
                "semanticQuery": "<string>",
                "filterExpression": "<string or null>"
              }

            - If there are no metadata filters, set "filterExpression" to null.
            """;

        String rawResponse = this.chatClient
                .prompt()
                .system(systemPrompt)
                .user(userQuery)
                .call()
                .content();

        String json = extractJson(rawResponse);

        try {
            return objectMapper.readValue(json, QueryPlan.class);
        } catch (Exception e) {
            // Fallback: if parsing fails
            QueryPlan fallback = new QueryPlan();
            fallback.setSemanticQuery(userQuery);
            fallback.setFilterExpression(null);
            return fallback;
        }
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
