package vn.edu.uit.edulens.dto.response;

public class NotificationItemResponse {
    private String id;
    private String title;
    private String body;
    private String courseCode;
    private String createdAt;
    private String category;
    private boolean unread;

    public NotificationItemResponse() {}

    public NotificationItemResponse(String id, String title, String body, String courseCode,
                                    String createdAt, String category, boolean unread) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.courseCode = courseCode;
        this.createdAt = createdAt;
        this.category = category;
        this.unread = unread;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }
}
