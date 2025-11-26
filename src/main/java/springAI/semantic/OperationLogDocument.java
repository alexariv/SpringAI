package springAI.semantic;

import java.util.List;

public class OperationLogDocument {

    private String description;
    private String id;
    private String owner;
    private String createdDate;
    private String eventStart;
    private String level;
    private String state;
    private String logbooksName;
    private List<String> tagsName;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getEventStart() { return eventStart; }
    public void setEventStart(String eventStart) { this.eventStart = eventStart; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getLogbooksName() { return logbooksName; }
    public void setLogbooksName(String logbooksName) { this.logbooksName = logbooksName; }

    public List<String> getTagsName() { return tagsName; }
    public void setTagsName(List<String> tagsName) { this.tagsName = tagsName; }
}
