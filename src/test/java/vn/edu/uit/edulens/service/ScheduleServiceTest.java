package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uit.edulens.dto.response.ClassScheduleResponse;
import vn.edu.uit.edulens.dto.response.ScheduleExceptionResponse;
import vn.edu.uit.edulens.dto.response.SemesterScheduleResponse;
import vn.edu.uit.edulens.dto.response.TodayScheduleItem;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.ClassSchedule;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.ScheduleException;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionStatus;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionType;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.ClassMemberRepository;
import vn.edu.uit.edulens.repository.ClassScheduleRepository;
import vn.edu.uit.edulens.repository.CourseClassRepository;
import vn.edu.uit.edulens.repository.ScheduleExceptionRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private CourseClassRepository courseClassRepository;
    @Mock
    private ClassScheduleRepository classScheduleRepository;
    @Mock
    private ScheduleExceptionRepository scheduleExceptionRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private UUID lecturerId;
    private UUID studentId;
    private UUID otherUserId;
    private UUID classId;
    private CourseClass courseClass;
    private ClassSchedule schedule1;
    private ClassSchedule schedule2;

    @BeforeEach
    void setUp() {
        lecturerId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        classId = UUID.randomUUID();

        User lecturer = User.builder()
                .id(lecturerId)
                .fullName("ThS. Tran Giang Vien")
                .role(Role.ROLE_LECTURER)
                .build();

        courseClass = CourseClass.builder()
                .id(classId)
                .code("SE104")
                .name("Nhập môn Công nghệ Phần mềm")
                .semester("HK1-2026")
                .room("C201")
                .status(ClassStatus.ACTIVE)
                .lecturer(lecturer)
                .build();

        schedule1 = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(2) // Tuesday
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 0))
                .room("C201")
                .startDate(LocalDate.now().minusWeeks(2))
                .endDate(LocalDate.now().plusWeeks(10))
                .build();

        schedule2 = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(4) // Thursday
                .startTime(LocalTime.of(13, 30))
                .endTime(LocalTime.of(17, 0))
                .room("C201")
                .startDate(LocalDate.now().minusWeeks(2))
                .endDate(LocalDate.now().plusWeeks(10))
                .build();
    }

    @Test
    @DisplayName("getClassSchedules - Giảng viên xem lịch học lớp mình quản lý thành công")
    void getClassSchedules_Lecturer_Success() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classScheduleRepository.findByCourseClassId(classId)).thenReturn(List.of(schedule1, schedule2));

        List<ClassScheduleResponse> responses = scheduleService.getClassSchedules(classId, lecturerId);

        assertEquals(2, responses.size());
        assertEquals("SE104", responses.get(0).getClassCode());
        assertEquals("Nhập môn Công nghệ Phần mềm", responses.get(0).getClassName());
        assertEquals(schedule1.getId(), responses.get(0).getId());
    }

    @Test
    @DisplayName("getClassSchedules - Sinh viên trong lớp xem lịch học định kỳ thành công")
    void getClassSchedules_StudentEnrolled_Success() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, studentId)).thenReturn(true);
        when(classScheduleRepository.findByCourseClassId(classId)).thenReturn(List.of(schedule1));

        List<ClassScheduleResponse> responses = scheduleService.getClassSchedules(classId, studentId);

        assertEquals(1, responses.size());
        assertEquals("SE104", responses.get(0).getClassCode());
        assertEquals("Nhập môn Công nghệ Phần mềm", responses.get(0).getClassName());
    }

    @Test
    @DisplayName("getClassSchedules - Người dùng không phải giảng viên và không trong lớp bị 403 Forbidden")
    void getClassSchedules_Forbidden_WhenNotLecturerNorEnrolled() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, otherUserId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                scheduleService.getClassSchedules(classId, otherUserId)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("getClassExceptions - Giảng viên lấy toàn bộ ngoại lệ của lớp (DRAFT và PUBLISHED)")
    void getClassExceptions_Lecturer_ReturnsAllExceptions() {
        ScheduleException exDraft = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .exceptionType(ScheduleExceptionType.CANCELLED)
                .status(ScheduleExceptionStatus.DRAFT)
                .originalDate(LocalDate.now())
                .build();

        ScheduleException exPub = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .exceptionType(ScheduleExceptionType.MODIFIED)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .originalDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(11, 30))
                .room("C202")
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(scheduleExceptionRepository.findByCourseClassIdOrderByOriginalDateDesc(classId))
                .thenReturn(List.of(exPub, exDraft));

        List<ScheduleExceptionResponse> result = scheduleService.getClassExceptions(classId, lecturerId, Role.ROLE_LECTURER);

        assertEquals(2, result.size());
        assertEquals("SE104", result.get(0).getClassCode());
        assertEquals("Nhập môn Công nghệ Phần mềm", result.get(0).getClassName());
    }

    @Test
    @DisplayName("getClassExceptions - Sinh viên chỉ lấy ngoại lệ PUBLISHED của lớp")
    void getClassExceptions_Student_ReturnsOnlyPublishedExceptions() {
        ScheduleException exPub = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .exceptionType(ScheduleExceptionType.MODIFIED)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .originalDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(11, 30))
                .room("C202")
                .build();

        when(courseClassRepository.existsById(classId)).thenReturn(true);
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, studentId)).thenReturn(true);
        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusOrderByOriginalDateDesc(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED
        )).thenReturn(List.of(exPub));

        List<ScheduleExceptionResponse> result = scheduleService.getClassExceptions(classId, studentId, Role.ROLE_STUDENT);

        assertEquals(1, result.size());
        assertEquals(ScheduleExceptionStatus.PUBLISHED, result.get(0).getStatus());
        assertEquals("SE104", result.get(0).getClassCode());
    }

    @Test
    @DisplayName("getClassExceptions - Sinh viên chưa tham gia lớp bị 403 Forbidden")
    void getClassExceptions_StudentNotEnrolled_ThrowsForbidden() {
        when(courseClassRepository.existsById(classId)).thenReturn(true);
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, otherUserId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                scheduleService.getClassExceptions(classId, otherUserId, Role.ROLE_STUDENT)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("getTodaySchedule - Hỗ trợ Giảng viên và trả về buổi CANCELLED với scheduleType CANCELLED")
    void getTodaySchedule_Lecturer_IncludesCancelledSession() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int todayDow = today.getDayOfWeek().getValue();

        ClassSchedule cancelledSchedule = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(todayDow)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 0))
                .room("C201")
                .build();

        ClassSchedule regularSchedule = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(todayDow)
                .startTime(LocalTime.of(13, 30))
                .endTime(LocalTime.of(17, 0))
                .room("C201")
                .build();

        ScheduleException cancelException = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .classSchedule(cancelledSchedule)
                .exceptionType(ScheduleExceptionType.CANCELLED)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .originalDate(today)
                .build();

        ScheduleException makeupException = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .exceptionType(ScheduleExceptionType.MAKEUP)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .occurrenceDate(today)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(21, 0))
                .room("B101")
                .build();

        when(courseClassRepository.findByLecturerId(lecturerId)).thenReturn(List.of(courseClass));
        when(classScheduleRepository.findByCourseClassIdInAndDayOfWeekAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                List.of(classId), todayDow, today, today
        )).thenReturn(List.of(cancelledSchedule, regularSchedule));

        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusAndOriginalDate(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED, today
        )).thenReturn(List.of(cancelException));

        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusAndOccurrenceDate(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED, today
        )).thenReturn(List.of(makeupException));

        List<TodayScheduleItem> result = scheduleService.getTodaySchedule(lecturerId, Role.ROLE_LECTURER);

        assertEquals(3, result.size());

        // Item 1: Cancelled schedule (7:30)
        assertEquals("CANCELLED", result.get(0).getScheduleType());
        assertEquals("SE104", result.get(0).getClassCode());
        assertEquals(LocalTime.of(7, 30), result.get(0).getStartTime());

        // Item 2: Regular schedule (13:30)
        assertEquals("REGULAR", result.get(1).getScheduleType());
        assertEquals("SE104", result.get(1).getClassCode());
        assertEquals(LocalTime.of(13, 30), result.get(1).getStartTime());

        // Item 3: Makeup schedule (18:00)
        assertEquals("MAKEUP", result.get(2).getScheduleType());
        assertEquals(LocalTime.of(18, 0), result.get(2).getStartTime());
    }

    @Test
    @DisplayName("getTodaySchedule - Sinh viên có lịch học bù và buổi học bị điều chỉnh (MODIFIED)")
    void getTodaySchedule_Student_ModifiedSchedule() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int todayDow = today.getDayOfWeek().getValue();

        ClassMember member = ClassMember.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .student(User.builder().id(studentId).build())
                .build();

        ClassSchedule schedule = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(todayDow)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 0))
                .room("C201")
                .build();

        ScheduleException modException = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .classSchedule(schedule)
                .exceptionType(ScheduleExceptionType.MODIFIED)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .originalDate(today)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(11, 30))
                .room("C205")
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(member));
        when(classScheduleRepository.findByCourseClassIdInAndDayOfWeekAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                List.of(classId), todayDow, today, today
        )).thenReturn(List.of(schedule));

        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusAndOriginalDate(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED, today
        )).thenReturn(List.of(modException));

        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusAndOccurrenceDate(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED, today
        )).thenReturn(Collections.emptyList());

        List<TodayScheduleItem> result = scheduleService.getTodaySchedule(studentId, Role.ROLE_STUDENT);

        assertEquals(1, result.size());
        assertEquals("MODIFIED", result.get(0).getScheduleType());
        assertEquals(LocalTime.of(8, 0), result.get(0).getStartTime());
        assertEquals("C205", result.get(0).getRoom());
    }

    @Test
    @DisplayName("getSemesterSchedule - Giảng viên lấy thời khóa biểu sắp xếp theo thứ và giờ học")
    void getSemesterSchedule_Lecturer_ReturnsSortedSchedulesAndExceptions() {
        ClassSchedule sMon = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(2) // Monday/Tuesday
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(16, 0))
                .build();

        ClassSchedule sFri = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(6)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 0))
                .build();

        ClassSchedule sMonEarly = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .dayOfWeek(2)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 0))
                .build();

        ScheduleException ex1 = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .exceptionType(ScheduleExceptionType.CANCELLED)
                .status(ScheduleExceptionStatus.PUBLISHED)
                .originalDate(LocalDate.of(2026, 11, 20))
                .build();

        when(courseClassRepository.findByLecturerId(lecturerId)).thenReturn(List.of(courseClass));
        when(classScheduleRepository.findByCourseClassIdIn(List.of(classId)))
                .thenReturn(List.of(sMon, sFri, sMonEarly));
        when(scheduleExceptionRepository.findByCourseClassIdInAndStatusOrderByOriginalDateDesc(
                List.of(classId), ScheduleExceptionStatus.PUBLISHED
        )).thenReturn(List.of(ex1));

        SemesterScheduleResponse response = scheduleService.getSemesterSchedule(lecturerId, Role.ROLE_LECTURER);

        assertNotNull(response);
        assertEquals(3, response.getRegularSchedules().size());
        // Verify sorting: dayOfWeek 2 (7:30) then dayOfWeek 2 (13:00) then dayOfWeek 6 (7:30)
        assertEquals(sMonEarly.getId(), response.getRegularSchedules().get(0).getId());
        assertEquals(sMon.getId(), response.getRegularSchedules().get(1).getId());
        assertEquals(sFri.getId(), response.getRegularSchedules().get(2).getId());

        assertEquals(1, response.getScheduleExceptions().size());
        assertEquals("SE104", response.getScheduleExceptions().get(0).getClassCode());
    }

    @Test
    @DisplayName("getSemesterSchedule - Không có lớp học trả về danh sách rỗng")
    void getSemesterSchedule_EmptyClasses_ReturnsEmptyLists() {
        when(courseClassRepository.findByLecturerId(lecturerId)).thenReturn(Collections.emptyList());

        SemesterScheduleResponse response = scheduleService.getSemesterSchedule(lecturerId, Role.ROLE_LECTURER);

        assertNotNull(response);
        assertTrue(response.getRegularSchedules().isEmpty());
        assertTrue(response.getScheduleExceptions().isEmpty());
    }
}
