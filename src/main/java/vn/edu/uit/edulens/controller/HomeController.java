package vn.edu.uit.edulens.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.AssignmentItem;
import vn.edu.uit.edulens.dto.response.CourseClassItem;
import vn.edu.uit.edulens.dto.response.HomeSummaryResponse;
import vn.edu.uit.edulens.entity.Assignment;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.repository.AssignmentRepository;
import vn.edu.uit.edulens.repository.ClassMemberRepository;
import vn.edu.uit.edulens.repository.CourseClassRepository;
import vn.edu.uit.edulens.repository.SubmissionRepository;
import vn.edu.uit.edulens.security.CustomUserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final ClassMemberRepository classMemberRepository;
    private final CourseClassRepository courseClassRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    public HomeController(
            ClassMemberRepository classMemberRepository,
            CourseClassRepository courseClassRepository,
            AssignmentRepository assignmentRepository,
            SubmissionRepository submissionRepository
    ) {
        this.classMemberRepository = classMemberRepository;
        this.courseClassRepository = courseClassRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> getHomeSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(new HomeSummaryResponse()));
        }

        UUID userId = userDetails.getId();
        Role role = userDetails.getUser().getRole();

        List<CourseClassItem> courses = new ArrayList<>();
        List<AssignmentItem> dueSoon = new ArrayList<>();

        if (role == Role.ROLE_STUDENT) {
            List<ClassMember> memberships = classMemberRepository.findByStudentId(userId);
            for (ClassMember member : memberships) {
                CourseClass cc = member.getCourseClass();
                if (cc != null) {
                    long enrolled = classMemberRepository.countByCourseClassId(cc.getId());
                    courses.add(new CourseClassItem(
                            cc.getId(),
                            cc.getCode(),
                            cc.getName(),
                            cc.getSemester(),
                            60,
                            (int) enrolled
                    ));

                    List<Assignment> assignments = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(cc.getId());
                    for (Assignment a : assignments) {
                        boolean submitted = submissionRepository.existsByAssignmentIdAndStudentId(a.getId(), userId);
                        dueSoon.add(new AssignmentItem(
                                a.getId(),
                                a.getTitle(),
                                cc.getName(),
                                a.getDeadline() != null ? a.getDeadline().toString() : "",
                                submitted
                        ));
                    }
                }
            }
        } else {
            List<CourseClass> classes = courseClassRepository.findByLecturerId(userId);
            for (CourseClass cc : classes) {
                long enrolled = classMemberRepository.countByCourseClassId(cc.getId());
                courses.add(new CourseClassItem(
                        cc.getId(),
                        cc.getCode(),
                        cc.getName(),
                        cc.getSemester(),
                        75,
                        (int) enrolled
                ));

                List<Assignment> assignments = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(cc.getId());
                for (Assignment a : assignments) {
                    dueSoon.add(new AssignmentItem(
                            a.getId(),
                            a.getTitle(),
                            cc.getName(),
                            a.getDeadline() != null ? a.getDeadline().toString() : "",
                            false
                    ));
                }
            }
        }

        int pendingCount = 0;
        for (AssignmentItem item : dueSoon) {
            if (!item.isSubmitted()) {
                pendingCount++;
            }
        }

        HomeSummaryResponse response = new HomeSummaryResponse(
                courses.size(),
                pendingCount,
                courses,
                dueSoon
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
