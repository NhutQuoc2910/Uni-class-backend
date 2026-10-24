package vn.edu.uit.edulens.dto.response;

import java.util.UUID;

public class AssignmentItem {
    private UUID id;
    private String title;
    private String className;
    private String dueDate;
    private boolean submitted;

    public AssignmentItem() {}

    public AssignmentItem(UUID id, String title, String className, String dueDate, boolean submitted) {
        this.id = id;
        this.title = title;
        this.className = className;
        this.dueDate = dueDate;
        this.submitted = submitted;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }
}
