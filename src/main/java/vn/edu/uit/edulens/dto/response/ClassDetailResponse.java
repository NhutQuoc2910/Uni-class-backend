package vn.edu.uit.edulens.dto.response;

import java.util.List;
import java.util.UUID;

public class ClassDetailResponse {
    private UUID id;
    private String code;
    private String name;
    private String semester;
    private String room;
    private String lecturerName;
    private String inviteCode;
    private int enrolledCount;
    private int attendanceRate;
    private int presentCount;
    private int absentCount;
    private int totalSessions;

    private List<AnnouncementItem> announcements;
    private List<MaterialItem> materials;
    private List<ClassAssignmentItem> assignments;
    private List<AttendanceSessionRecordItem> attendanceRecords;

    public ClassDetailResponse() {}

    public ClassDetailResponse(UUID id, String code, String name, String semester, String room,
                               String lecturerName, String inviteCode, int enrolledCount,
                               int attendanceRate, int presentCount, int absentCount, int totalSessions,
                               List<AnnouncementItem> announcements, List<MaterialItem> materials,
                               List<ClassAssignmentItem> assignments,
                               List<AttendanceSessionRecordItem> attendanceRecords) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.semester = semester;
        this.room = room;
        this.lecturerName = lecturerName;
        this.inviteCode = inviteCode;
        this.enrolledCount = enrolledCount;
        this.attendanceRate = attendanceRate;
        this.presentCount = presentCount;
        this.absentCount = absentCount;
        this.totalSessions = totalSessions;
        this.announcements = announcements;
        this.materials = materials;
        this.assignments = assignments;
        this.attendanceRecords = attendanceRecords;
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

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    public int getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(int attendanceRate) {
        this.attendanceRate = attendanceRate;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public List<AnnouncementItem> getAnnouncements() {
        return announcements;
    }

    public void setAnnouncements(List<AnnouncementItem> announcements) {
        this.announcements = announcements;
    }

    public List<MaterialItem> getMaterials() {
        return materials;
    }

    public void setMaterials(List<MaterialItem> materials) {
        this.materials = materials;
    }

    public List<ClassAssignmentItem> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<ClassAssignmentItem> assignments) {
        this.assignments = assignments;
    }

    public List<AttendanceSessionRecordItem> getAttendanceRecords() {
        return attendanceRecords;
    }

    public void setAttendanceRecords(List<AttendanceSessionRecordItem> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }

    public static class AnnouncementItem {
        private String id;
        private String title;
        private String content;
        private String createdAt;
        private boolean urgent;
        private boolean unread;

        public AnnouncementItem() {}

        public AnnouncementItem(String id, String title, String content, String createdAt, boolean urgent, boolean unread) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.createdAt = createdAt;
            this.urgent = urgent;
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

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public boolean isUrgent() {
            return urgent;
        }

        public void setUrgent(boolean urgent) {
            this.urgent = urgent;
        }

        public boolean isUnread() {
            return unread;
        }

        public void setUnread(boolean unread) {
            this.unread = unread;
        }
    }

    public static class MaterialItem {
        private UUID id;
        private String title;
        private String fileUrl;
        private String fileType;
        private String createdAt;

        public MaterialItem() {}

        public MaterialItem(UUID id, String title, String fileUrl, String fileType, String createdAt) {
            this.id = id;
            this.title = title;
            this.fileUrl = fileUrl;
            this.fileType = fileType;
            this.createdAt = createdAt;
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

        public String getFileUrl() {
            return fileUrl;
        }

        public void setFileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class ClassAssignmentItem {
        private UUID id;
        private String title;
        private String description;
        private String dueDate;
        private String attachmentUrl;
        private boolean submitted;
        private Double score;
        private String feedback;

        public ClassAssignmentItem() {}

        public ClassAssignmentItem(UUID id, String title, String description, String dueDate,
                                   String attachmentUrl, boolean submitted, Double score, String feedback) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.dueDate = dueDate;
            this.attachmentUrl = attachmentUrl;
            this.submitted = submitted;
            this.score = score;
            this.feedback = feedback;
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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDueDate() {
            return dueDate;
        }

        public void setDueDate(String dueDate) {
            this.dueDate = dueDate;
        }

        public String getAttachmentUrl() {
            return attachmentUrl;
        }

        public void setAttachmentUrl(String attachmentUrl) {
            this.attachmentUrl = attachmentUrl;
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public void setSubmitted(boolean submitted) {
            this.submitted = submitted;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }
    }

    public static class AttendanceSessionRecordItem {
        private UUID sessionId;
        private int sessionNumber;
        private String date;
        private String status;
        private String scannedAt;

        public AttendanceSessionRecordItem() {}

        public AttendanceSessionRecordItem(UUID sessionId, int sessionNumber, String date, String status, String scannedAt) {
            this.sessionId = sessionId;
            this.sessionNumber = sessionNumber;
            this.date = date;
            this.status = status;
            this.scannedAt = scannedAt;
        }

        public UUID getSessionId() {
            return sessionId;
        }

        public void setSessionId(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public int getSessionNumber() {
            return sessionNumber;
        }

        public void setSessionNumber(int sessionNumber) {
            this.sessionNumber = sessionNumber;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getScannedAt() {
            return scannedAt;
        }

        public void setScannedAt(String scannedAt) {
            this.scannedAt = scannedAt;
        }
    }
}
