package vn.edu.uit.edulens.dto.response;

import java.util.UUID;

public class CourseClassItem {
    private UUID id;
    private String code;
    private String name;
    private String semester;
    private String room;
    private String lecturerName;
    private String inviteCode;
    private String status;
    private int progress;
    private int enrolledCount;

    public CourseClassItem() {}

    public CourseClassItem(UUID id, String code, String name, String semester, int progress, int enrolledCount) {
        this(id, code, name, semester, null, null, null, "ACTIVE", progress, enrolledCount);
    }

    public CourseClassItem(UUID id, String code, String name, String semester, String room, String lecturerName, int progress, int enrolledCount) {
        this(id, code, name, semester, room, lecturerName, null, "ACTIVE", progress, enrolledCount);
    }

    public CourseClassItem(UUID id, String code, String name, String semester, String room, String lecturerName,
                           String inviteCode, String status, int progress, int enrolledCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.semester = semester;
        this.room = room;
        this.lecturerName = lecturerName;
        this.inviteCode = inviteCode;
        this.status = status != null ? status : "ACTIVE";
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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getLecturerName() {
        return lecturerName;
    }

    public void setLecturerName(String lecturerName) {
        this.lecturerName = lecturerName;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
