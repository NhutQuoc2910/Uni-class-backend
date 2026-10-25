package vn.edu.uit.edulens.dto.response;

import java.util.ArrayList;
import java.util.List;

public class HomeSummaryResponse {
    private int activeCoursesCount;
    private int pendingAssignmentsCount;
    private int attendanceRate;
    private List<CourseClassItem> courses = new ArrayList<>();
    private List<AssignmentItem> dueSoonAssignments = new ArrayList<>();

    public HomeSummaryResponse() {}

    public HomeSummaryResponse(int activeCoursesCount, int pendingAssignmentsCount, List<CourseClassItem> courses, List<AssignmentItem> dueSoonAssignments) {
        this(activeCoursesCount, pendingAssignmentsCount, 0, courses, dueSoonAssignments);
    }

    public HomeSummaryResponse(int activeCoursesCount, int pendingAssignmentsCount, int attendanceRate, List<CourseClassItem> courses, List<AssignmentItem> dueSoonAssignments) {
        this.activeCoursesCount = activeCoursesCount;
        this.pendingAssignmentsCount = pendingAssignmentsCount;
        this.attendanceRate = attendanceRate;
        this.courses = courses != null ? courses : new ArrayList<>();
        this.dueSoonAssignments = dueSoonAssignments != null ? dueSoonAssignments : new ArrayList<>();
    }

    public int getActiveCoursesCount() {
        return activeCoursesCount;
    }

    public void setActiveCoursesCount(int activeCoursesCount) {
        this.activeCoursesCount = activeCoursesCount;
    }

    public int getPendingAssignmentsCount() {
        return pendingAssignmentsCount;
    }

    public void setPendingAssignmentsCount(int pendingAssignmentsCount) {
        this.pendingAssignmentsCount = pendingAssignmentsCount;
    }

    public int getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(int attendanceRate) {
        this.attendanceRate = attendanceRate;
    }

    public List<CourseClassItem> getCourses() {
        return courses;
    }

    public void setCourses(List<CourseClassItem> courses) {
        this.courses = courses;
    }

    public List<AssignmentItem> getDueSoonAssignments() {
        return dueSoonAssignments;
    }

    public void setDueSoonAssignments(List<AssignmentItem> dueSoonAssignments) {
        this.dueSoonAssignments = dueSoonAssignments;
    }
}
