package vn.edu.uit.edulens.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.entity.*;
import vn.edu.uit.edulens.entity.enums.*;
import vn.edu.uit.edulens.repository.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    private final UserRepository userRepository;
    private final CourseClassRepository courseClassRepository;
    private final ClassMemberRepository classMemberRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MaterialRepository materialRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEMO_PDF_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";

    @Transactional
    public void seed() {
        if (userRepository.findByEmail("lecturer@uit.edu.vn").isPresent()) {
            log.info("Demo data already exists (lecturer@uit.edu.vn found). Skipping demo seed.");
            return;
        }

        log.info("Starting demo data seeding...");
        String encodedPassword = passwordEncoder.encode("Demo@123");

        // 1. Users
        User lecturer = userRepository.save(User.builder()
                .email("lecturer@uit.edu.vn")
                .fullName("ThS. Trần Minh Khoa")
                .phone("0901000001")
                .passwordHash(encodedPassword)
                .role(Role.ROLE_LECTURER)
                .status(UserStatus.ACTIVE)
                .build());

        User student1 = userRepository.save(User.builder()
                .email("student1@uit.edu.vn")
                .fullName("Nguyễn Văn An")
                .phone("0901000011")
                .passwordHash(encodedPassword)
                .role(Role.ROLE_STUDENT)
                .status(UserStatus.ACTIVE)
                .build());

        User student2 = userRepository.save(User.builder()
                .email("student2@uit.edu.vn")
                .fullName("Trần Thị Bình")
                .phone("0901000012")
                .passwordHash(encodedPassword)
                .role(Role.ROLE_STUDENT)
                .status(UserStatus.ACTIVE)
                .build());

        User student3 = userRepository.save(User.builder()
                .email("student3@uit.edu.vn")
                .fullName("Lê Hoàng Cường")
                .phone("0901000013")
                .passwordHash(encodedPassword)
                .role(Role.ROLE_STUDENT)
                .status(UserStatus.ACTIVE)
                .build());

        List<User> students = List.of(student1, student2, student3);

        // 2. Classes
        List<CourseClass> classes = List.of(
                CourseClass.builder().code("NT118.Q11").name("Phát triển ứng dụng trên thiết bị di động").semester("HK1 2026-2027").room("B1.12").inviteCode("NT118DEMO").lecturer(lecturer).status(ClassStatus.ACTIVE).build(),
                CourseClass.builder().code("NT101.Q12").name("Nhập môn Mạng máy tính").semester("HK1 2026-2027").room("C2.05").inviteCode("NT101DEMO").lecturer(lecturer).status(ClassStatus.ACTIVE).build(),
                CourseClass.builder().code("NT208.Q13").name("Lập trình ứng dụng Web").semester("HK1 2026-2027").room("B4.08").inviteCode("NT208DEMO").lecturer(lecturer).status(ClassStatus.ACTIVE).build(),
                CourseClass.builder().code("NT132.Q14").name("Quản trị mạng và hệ thống").semester("HK1 2026-2027").room("E3.02").inviteCode("NT132DEMO").lecturer(lecturer).status(ClassStatus.ACTIVE).build()
        );
        classes = courseClassRepository.saveAll(classes);

        // 3. Class Members
        List<ClassMember> members = new ArrayList<>();
        for (CourseClass cc : classes) {
            for (User s : students) {
                members.add(ClassMember.builder().courseClass(cc).student(s).build());
            }
        }
        classMemberRepository.saveAll(members);

        // 4. Assignments, Materials, Sessions, Records per Class
        Instant now = Instant.now();

        for (CourseClass cc : classes) {
            // Materials
            materialRepository.saveAll(List.of(
                    Material.builder().courseClass(cc).title("Đề cương môn học " + cc.getCode()).fileUrl(DEMO_PDF_URL).fileType("pdf").build(),
                    Material.builder().courseClass(cc).title("Slide Bài giảng Chương 1 - Giới thiệu tổng quan").fileUrl(DEMO_PDF_URL).fileType("pdf").build()
            ));

            // Assignments
            Assignment a1 = assignmentRepository.save(Assignment.builder()
                    .courseClass(cc)
                    .title("Bài tập 1: Cài đặt môi trường & Khởi tạo dự án")
                    .description("Nộp file báo cáo PDF các bước cài đặt và ảnh chụp kết quả.")
                    .deadline(now.minus(Duration.ofDays(3)))
                    .attachmentUrl(DEMO_PDF_URL)
                    .build());

            Assignment a2 = assignmentRepository.save(Assignment.builder()
                    .courseClass(cc)
                    .title("Bài tập 2: Thiết kế giao diện & Cấu trúc Dữ liệu")
                    .description("Nộp source code zip hoặc file PDF thiết kế màn hình.")
                    .deadline(now.plus(Duration.ofDays(2)))
                    .attachmentUrl(DEMO_PDF_URL)
                    .build());

            Assignment a3 = assignmentRepository.save(Assignment.builder()
                    .courseClass(cc)
                    .title("Bài tập 3: Ghép nối REST API & Hoàn thiện báo cáo Giữa kỳ")
                    .description("Nộp file báo cáo tổng hợp và link repository GitHub.")
                    .deadline(now.plus(Duration.ofDays(10)))
                    .attachmentUrl(DEMO_PDF_URL)
                    .build());

            // Submissions
            submissionRepository.save(Submission.builder()
                    .assignment(a1).student(student1).fileUrl(DEMO_PDF_URL).score(8.5).feedback("Bài làm tốt, cấu trúc rõ ràng.").build());
            submissionRepository.save(Submission.builder()
                    .assignment(a1).student(student2).fileUrl(DEMO_PDF_URL).score(7.0).feedback("Cần chú ý thêm định dạng báo cáo.").build());
            submissionRepository.save(Submission.builder()
                    .assignment(a2).student(student1).fileUrl(DEMO_PDF_URL).score(null).feedback(null).build());

            // 5 Attendance Sessions (Closed)
            for (int i = 1; i <= 5; i++) {
                Instant sessionTime = now.minus(Duration.ofDays((6 - i) * 7L));
                AttendanceSession session = attendanceSessionRepository.save(AttendanceSession.builder()
                        .courseClass(cc)
                        .wifiBssid("00:11:22:33:44:55")
                        .latitude(10.8700)
                        .longitude(106.8031)
                        .radiusMeters(50.0)
                        .status(SessionStatus.CLOSED)
                        .createdAt(sessionTime)
                        .closedAt(sessionTime.plus(Duration.ofMinutes(15)))
                        .build());

                // Records:
                // student1: 5/5
                attendanceRecordRepository.save(AttendanceRecord.builder()
                        .session(session).student(student1).deviceId("device-demo-st1").status(AttendanceStatus.PRESENT).scannedAt(sessionTime.plus(Duration.ofMinutes(2))).build());

                // student2: 4/5 (vắng buổi 4)
                if (i != 4) {
                    attendanceRecordRepository.save(AttendanceRecord.builder()
                            .session(session).student(student2).deviceId("device-demo-st2").status(AttendanceStatus.PRESENT).scannedAt(sessionTime.plus(Duration.ofMinutes(3))).build());
                }

                // student3: 3/5 (vắng buổi 2 và buổi 5)
                if (i != 2 && i != 5) {
                    attendanceRecordRepository.save(AttendanceRecord.builder()
                            .session(session).student(student3).deviceId("device-demo-st3").status(AttendanceStatus.PRESENT).scannedAt(sessionTime.plus(Duration.ofMinutes(4))).build());
                }
            }
        }

        log.info("Demo data seeded successfully!");
    }
}
