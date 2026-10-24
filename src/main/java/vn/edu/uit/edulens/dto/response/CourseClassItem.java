package vn.edu.uit.edulens.dto.response;

import java.util.UUID;

public class CourseClassItem {
    private UUID id;
    private String code;
    private String name;
    private String semester;
    private int progress;
    private int enrolledCount;

    public CourseClassItem() {}

    public CourseClassItem(UUID id, String code, String name, String semester, int progress, int enrolledCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.semester = semester;
        this.progress = progress;
        this.enrolledCount = enrolledCount;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }
}
