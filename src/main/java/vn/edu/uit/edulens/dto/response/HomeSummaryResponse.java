package vn.edu.uit.edulens.dto.response;

import java.util.ArrayList;
import java.util.List;

public class HomeSummaryResponse {
    private int activeCoursesCount;
    private int pendingAssignmentsCount;
    private List<CourseClassItem> courses = new ArrayList<>();
    private List<AssignmentItem> dueSoonAssignments = new ArrayList<>();

    public HomeSummaryResponse() {}

    public HomeSummaryResponse(int activeCoursesCount, int pendingAssignmentsCount, List<CourseClassItem> courses, List<AssignmentItem> dueSoonAssignments) {
        this.activeCoursesCount = activeCoursesCount;
        this.pendingAssignmentsCount = pendingAssignmentsCount;
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
