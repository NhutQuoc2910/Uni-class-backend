package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.CreateClassScheduleRequest;
import vn.edu.uit.edulens.dto.request.CreateScheduleExceptionRequest;
import vn.edu.uit.edulens.dto.response.ClassScheduleResponse;
import vn.edu.uit.edulens.dto.response.ScheduleExceptionResponse;
import vn.edu.uit.edulens.dto.response.SemesterScheduleResponse;
import vn.edu.uit.edulens.dto.response.TodayScheduleItem;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.ClassSchedule;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.ScheduleException;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final CourseClassRepository courseClassRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final ClassMemberRepository classMemberRepository;

    @Transactional
    public ClassScheduleResponse createSchedule(
            UUID classId,
            UUID lecturerId,
            CreateClassScheduleRequest request
    ) {
        CourseClass cc = getOwnedClass(classId, lecturerId);

        validateScheduleRequest(request);

        ClassSchedule schedule = ClassSchedule.builder()
                .id(UUID.randomUUID())
                .courseClass(cc)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .room(normalizeRoom(request.getRoom(), cc.getRoom()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        validateNoRecurringOverlap(schedule, null);

        return toScheduleResponse(classScheduleRepository.save(schedule));
    }


    @Transactional(readOnly = true)
    public List<ClassScheduleResponse> getClassSchedules(
            UUID classId,
            UUID userId
    ) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        boolean isLecturer = cc.getLecturer() != null && cc.getLecturer().getId().equals(userId);
        boolean isEnrolled = classMemberRepository.existsByCourseClassIdAndStudentId(classId, userId);

        if (!isLecturer && !isEnrolled) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        List<ClassSchedule> schedules =
                classScheduleRepository.findByCourseClassId(classId);

        List<ClassScheduleResponse> result = new ArrayList<>();

        for (ClassSchedule schedule : schedules) {
            result.add(toScheduleResponse(schedule));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<ScheduleExceptionResponse> getClassExceptions(
            UUID classId,
            UUID userId,
            Role role
    ) {
        List<ScheduleException> exceptions;

        if (role == Role.ROLE_LECTURER) {
            getOwnedClass(classId, userId);
            exceptions = scheduleExceptionRepository
                    .findByCourseClassIdOrderByOriginalDateDesc(classId);
        } else if (role == Role.ROLE_STUDENT) {
            if (!courseClassRepository.existsById(classId)) {
                throw new AppException(ErrorCode.CLASS_NOT_FOUND);
            }
            if (!classMemberRepository.existsByCourseClassIdAndStudentId(classId, userId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            exceptions = scheduleExceptionRepository
                    .findByCourseClassIdInAndStatusOrderByOriginalDateDesc(
                            List.of(classId),
                            ScheduleExceptionStatus.PUBLISHED
                    );
        } else {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        List<ScheduleExceptionResponse> result = new ArrayList<>();
        for (ScheduleException ex : exceptions) {
            result.add(toExceptionResponse(ex));
        }
        return result;
    }


    @Transactional
    public ClassScheduleResponse updateSchedule(
            UUID classId,
            UUID scheduleId,
            UUID lecturerId,
            CreateClassScheduleRequest request
    ) {
        CourseClass cc = getOwnedClass(classId, lecturerId);

        validateScheduleRequest(request);

        ClassSchedule schedule = classScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule.getCourseClass() == null
                || !schedule.getCourseClass().getId().equals(classId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        schedule.setDayOfWeek(request.getDayOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setRoom(normalizeRoom(request.getRoom(), cc.getRoom()));
        schedule.setStartDate(request.getStartDate());
        schedule.setEndDate(request.getEndDate());

        validateNoRecurringOverlap(schedule, scheduleId);

        return toScheduleResponse(classScheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteSchedule(UUID classId, UUID scheduleId, UUID lecturerId) {
        CourseClass cc = getOwnedClass(classId, lecturerId);

        ClassSchedule schedule = classScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule.getCourseClass() == null || !schedule.getCourseClass().getId().equals(classId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        List<ScheduleException> exceptions = scheduleExceptionRepository.findByClassScheduleId(scheduleId);
        if (exceptions != null && !exceptions.isEmpty()) {
            for (ScheduleException ex : exceptions) {
                List<ScheduleException> dependents = scheduleExceptionRepository.findByRelatedExceptionId(ex.getId());
                if (dependents != null && !dependents.isEmpty()) {
                    for (ScheduleException dep : dependents) {
                        dep.setRelatedException(null);
                    }
                    scheduleExceptionRepository.saveAll(dependents);
                }
                ex.setRelatedException(null);
            }
            scheduleExceptionRepository.saveAll(exceptions);
            scheduleExceptionRepository.deleteAll(exceptions);
        }

        classScheduleRepository.delete(schedule);
    }

    private void validateScheduleRequest(CreateClassScheduleRequest request) {
        if (request == null
                || request.getDayOfWeek() == null
                || request.getStartTime() == null
                || request.getEndTime() == null
                || request.getStartDate() == null
                || request.getEndDate() == null) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Vui lòng nhập đầy đủ thứ, giờ học và thời gian áp dụng"
            );
        }

        if (request.getDayOfWeek() < 1 || request.getDayOfWeek() > 7) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Thứ trong tuần phải nằm trong khoảng từ 1 đến 7"
            );
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
    }

    private void validateNoRecurringOverlap(
            ClassSchedule target,
            UUID excludedScheduleId
    ) {
        List<ClassSchedule> existingSchedules =
                classScheduleRepository.findByCourseClassId(
                        target.getCourseClass().getId()
                );

        for (ClassSchedule existing : existingSchedules) {

            if (excludedScheduleId != null
                    && existing.getId().equals(excludedScheduleId)) {
                continue;
            }


            if (!existing.getDayOfWeek().equals(target.getDayOfWeek())) {
                continue;
            }

  
            boolean dateOverlap =
                    !target.getEndDate().isBefore(existing.getStartDate())
                    && !target.getStartDate().isAfter(existing.getEndDate());

            if (!dateOverlap) {
                continue;
            }

            boolean timeOverlap =
                    target.getStartTime().isBefore(existing.getEndTime())
                    && target.getEndTime().isAfter(existing.getStartTime());

            if (timeOverlap) {
                throw new AppException(
                        ErrorCode.SCHEDULE_CONFLICT,
                        "Lịch học bị trùng với lịch khác trong cùng lớp"
                );
            }
        }
    }

  

    private CourseClass getOwnedClass(UUID classId, UUID lecturerId) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (cc.getLecturer() == null
                || !cc.getLecturer().getId().equals(lecturerId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (cc.getStatus() == ClassStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CLASS_ARCHIVED);
        }

        return cc;
    }



    private String normalizeRoom(String room, String defaultRoom) {
        if (room != null && !room.isBlank()) {
            return room.trim();
        }
        return defaultRoom;
    }

    private ClassScheduleResponse toScheduleResponse(ClassSchedule schedule) {
        CourseClass cc = schedule.getCourseClass();
        return ClassScheduleResponse.builder()
                .id(schedule.getId())
                .classId(cc != null ? cc.getId() : null)
                .classCode(cc != null ? cc.getCode() : null)
                .className(cc != null ? cc.getName() : null)
                .dayOfWeek(schedule.getDayOfWeek())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .room(schedule.getRoom())
                .startDate(schedule.getStartDate())
                .endDate(schedule.getEndDate())
                .build();
    }

    @Transactional
    public ScheduleExceptionResponse createException(
            UUID classId,
            UUID lecturerId,
            CreateScheduleExceptionRequest request
    ) {
        CourseClass cc = getOwnedClass(classId, lecturerId);

        if (request == null || request.getExceptionType() == null) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_EXCEPTION);
        }

        ScheduleException exception = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(cc)
                .exceptionType(request.getExceptionType())
                .status(ScheduleExceptionStatus.DRAFT)
                .originalDate(request.getOriginalDate())
                .occurrenceDate(request.getOccurrenceDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .room(request.getRoom())
                .reason(request.getReason())
                .build();

        ScheduleExceptionType type = request.getExceptionType();

        if (type == ScheduleExceptionType.CANCELLED
                || type == ScheduleExceptionType.MODIFIED) {

            if (request.getClassScheduleId() == null
                    || request.getOriginalDate() == null) {
                throw new AppException(ErrorCode.INVALID_SCHEDULE_EXCEPTION);
            }

            ClassSchedule schedule = getScheduleInClass(
                    classId,
                    request.getClassScheduleId()
            );

            validateOriginalDate(schedule, request.getOriginalDate());

            boolean exists = scheduleExceptionRepository
                    .existsByClassScheduleIdAndOriginalDateAndExceptionTypeAndStatusNot(
                            schedule.getId(),
                            request.getOriginalDate(),
                            ScheduleExceptionType.CANCELLED,
                            ScheduleExceptionStatus.REVOKED
                    ) || scheduleExceptionRepository
                    .existsByClassScheduleIdAndOriginalDateAndExceptionTypeAndStatusNot(
                            schedule.getId(),
                            request.getOriginalDate(),
                            ScheduleExceptionType.MODIFIED,
                            ScheduleExceptionStatus.REVOKED
                    );

            if (exists) {
                throw new AppException(ErrorCode.SCHEDULE_EXCEPTION_EXISTS);
            }

            exception.setClassSchedule(schedule);

            if (type == ScheduleExceptionType.CANCELLED) {
                exception.setStartTime(null);
                exception.setEndTime(null);
                exception.setRoom(null);
                exception.setOccurrenceDate(null);
            } else {
                validateModifiedTime(schedule, request);
            }

        } else if (type == ScheduleExceptionType.MAKEUP) {

            if (request.getOccurrenceDate() == null
                    || request.getStartTime() == null
                    || request.getEndTime() == null
                    || !request.getEndTime().isAfter(request.getStartTime())) {
                throw new AppException(
                        ErrorCode.INVALID_SCHEDULE_EXCEPTION,
                        "Lịch học bù cần có ngày học, giờ bắt đầu và giờ kết thúc hợp lệ"
                );
            }

            if (request.getClassScheduleId() != null) {
                ClassSchedule schedule = getScheduleInClass(
                        classId,
                        request.getClassScheduleId()
                );
                exception.setClassSchedule(schedule);
            }

            if (request.getRelatedExceptionId() != null) {
                ScheduleException related = scheduleExceptionRepository
                        .findById(request.getRelatedExceptionId())
                        .orElseThrow(() ->
                                new AppException(ErrorCode.SCHEDULE_EXCEPTION_NOT_FOUND));

                if (!related.getCourseClass().getId().equals(classId)
                        || related.getExceptionType() != ScheduleExceptionType.CANCELLED
                        || related.getStatus() != ScheduleExceptionStatus.PUBLISHED) {
                    throw new AppException(
                            ErrorCode.INVALID_SCHEDULE_EXCEPTION,
                            "Chỉ có thể liên kết lịch học bù với một buổi đã hủy và được công bố"
                    );
                }

                exception.setRelatedException(related);

                if (request.getOriginalDate() == null) {
                    exception.setOriginalDate(related.getOriginalDate());
                }
            }

        } else {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_EXCEPTION);
        }

        return toExceptionResponse(scheduleExceptionRepository.save(exception));
    }
    @Transactional
    public ScheduleExceptionResponse publishException(
            UUID classId,
            UUID exceptionId,
            UUID lecturerId
    ) {
        getOwnedClass(classId, lecturerId);

        ScheduleException exception = getExceptionInClass(classId, exceptionId);

        if (exception.getStatus() != ScheduleExceptionStatus.DRAFT) {
            throw new AppException(ErrorCode.SCHEDULE_EXCEPTION_ALREADY_PROCESSED);
        }

        if (exception.getExceptionType() == ScheduleExceptionType.MAKEUP
                && exception.getRelatedException() != null
                && exception.getRelatedException().getStatus()
                        != ScheduleExceptionStatus.PUBLISHED) {
            throw new AppException(
                    ErrorCode.INVALID_SCHEDULE_EXCEPTION,
                    "Buổi học bị hủy liên kết chưa được công bố"
            );
        }

        exception.setStatus(ScheduleExceptionStatus.PUBLISHED);

        return toExceptionResponse(scheduleExceptionRepository.save(exception));
    }
    @Transactional
    public ScheduleExceptionResponse revokeException(
            UUID classId,
            UUID exceptionId,
            UUID lecturerId
    ) {
        getOwnedClass(classId, lecturerId);

        ScheduleException exception = getExceptionInClass(classId, exceptionId);

        if (exception.getStatus() == ScheduleExceptionStatus.REVOKED) {
            throw new AppException(ErrorCode.SCHEDULE_EXCEPTION_ALREADY_PROCESSED);
        }

        exception.setStatus(ScheduleExceptionStatus.REVOKED);

        return toExceptionResponse(scheduleExceptionRepository.save(exception));
    }

    private ClassSchedule getScheduleInClass(
        UUID classId,
        UUID scheduleId
    ) {
        ClassSchedule schedule = classScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule.getCourseClass() == null
                || !schedule.getCourseClass().getId().equals(classId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        return schedule;
    }

    private ScheduleException getExceptionInClass(
            UUID classId,
            UUID exceptionId
    ) {
        ScheduleException exception = scheduleExceptionRepository.findById(exceptionId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.SCHEDULE_EXCEPTION_NOT_FOUND));

        if (exception.getCourseClass() == null
                || !exception.getCourseClass().getId().equals(classId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        return exception;
    }

    private void validateOriginalDate(
            ClassSchedule schedule,
            LocalDate originalDate
    ) {
        if (originalDate.isBefore(schedule.getStartDate())
                || originalDate.isAfter(schedule.getEndDate())
                || originalDate.getDayOfWeek().getValue()
                        != schedule.getDayOfWeek()) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_DATE);
        }
    }

    private void validateModifiedTime(
            ClassSchedule schedule,
            CreateScheduleExceptionRequest request
    ) {
        LocalTime start = request.getStartTime() != null
                ? request.getStartTime()
                : schedule.getStartTime();

        LocalTime end = request.getEndTime() != null
                ? request.getEndTime()
                : schedule.getEndTime();

        if (!end.isAfter(start)) {
            throw new AppException(
                    ErrorCode.INVALID_SCHEDULE_EXCEPTION,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }

        if (request.getStartTime() == null
                && request.getEndTime() == null
                && (request.getRoom() == null || request.getRoom().isBlank())) {
            throw new AppException(
                    ErrorCode.INVALID_SCHEDULE_EXCEPTION,
                    "Cần cung cấp ít nhất giờ bắt đầu, giờ kết thúc hoặc phòng học mới"
            );
        }
    }

    private ScheduleExceptionResponse toExceptionResponse(
            ScheduleException exception
    ) {
        CourseClass cc = exception.getCourseClass();
        return ScheduleExceptionResponse.builder()
                .id(exception.getId())
                .classId(cc != null ? cc.getId() : null)
                .classCode(cc != null ? cc.getCode() : null)
                .className(cc != null ? cc.getName() : null)
                .classScheduleId(
                        exception.getClassSchedule() != null
                                ? exception.getClassSchedule().getId()
                                : null
                )
                .originalDate(exception.getOriginalDate())
                .occurrenceDate(exception.getOccurrenceDate())
                .exceptionType(exception.getExceptionType())
                .status(exception.getStatus())
                .startTime(exception.getStartTime())
                .endTime(exception.getEndTime())
                .room(exception.getRoom())
                .reason(exception.getReason())
                .relatedExceptionId(
                        exception.getRelatedException() != null
                                ? exception.getRelatedException().getId()
                                : null
                )
                .build();
    }

    private List<UUID> getClassIdsForUser(UUID userId, Role role) {
        if (role == Role.ROLE_LECTURER) {
            List<CourseClass> classes = courseClassRepository.findByLecturerId(userId);
            if (classes == null || classes.isEmpty()) {
                return Collections.emptyList();
            }
            return classes.stream()
                    .map(CourseClass::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } else if (role == Role.ROLE_STUDENT) {
            List<ClassMember> memberships = classMemberRepository.findByStudentId(userId);
            if (memberships == null || memberships.isEmpty()) {
                return Collections.emptyList();
            }
            return memberships.stream()
                    .map(ClassMember::getCourseClass)
                    .filter(Objects::nonNull)
                    .map(CourseClass::getId)
                    .distinct()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<TodayScheduleItem> getTodaySchedule(UUID studentId) {
        return getTodaySchedule(studentId, Role.ROLE_STUDENT);
    }

    @Transactional(readOnly = true)
    public List<TodayScheduleItem> getTodaySchedule(UUID userId, Role role) {

        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zoneId);

        int dayOfWeek = today.getDayOfWeek().getValue();

        List<UUID> classIds = getClassIdsForUser(userId, role);
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<TodayScheduleItem> result = new ArrayList<>();

        // 2. Lấy lịch định kỳ diễn ra hôm nay
        List<ClassSchedule> regularSchedules =
                classScheduleRepository
                        .findByCourseClassIdInAndDayOfWeekAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                classIds,
                                dayOfWeek,
                                today,
                                today
                        );

        // 3. Lấy các ngoại lệ đã công bố áp dụng cho buổi học hôm nay
        List<ScheduleException> originalExceptions =
                scheduleExceptionRepository
                        .findByCourseClassIdInAndStatusAndOriginalDate(
                                classIds,
                                ScheduleExceptionStatus.PUBLISHED,
                                today
                        );

        Map<UUID, ScheduleException> exceptionByScheduleId = new HashMap<>();

        for (ScheduleException exception : originalExceptions) {
            ScheduleExceptionType type = exception.getExceptionType();

            if ((type == ScheduleExceptionType.CANCELLED
                    || type == ScheduleExceptionType.MODIFIED)
                    && exception.getClassSchedule() != null) {

                exceptionByScheduleId.put(
                        exception.getClassSchedule().getId(),
                        exception
                );
            }
        }

        // 4. Xử lý lịch định kỳ và ngoại lệ
        for (ClassSchedule schedule : regularSchedules) {

            ScheduleException exception =
                    exceptionByScheduleId.get(schedule.getId());

            CourseClass cc = schedule.getCourseClass();

            if (cc == null) {
                continue;
            }

            // Buổi học bị hủy
            if (exception != null
                    && exception.getExceptionType()
                            == ScheduleExceptionType.CANCELLED) {
                result.add(TodayScheduleItem.builder()
                        .classId(cc.getId())
                        .scheduleId(schedule.getId())
                        .classCode(cc.getCode())
                        .className(cc.getName())
                        .date(today)
                        .startTime(schedule.getStartTime())
                        .endTime(schedule.getEndTime())
                        .room(schedule.getRoom())
                        .scheduleType("CANCELLED")
                        .build());
                continue;
            }

            boolean modified = exception != null
                    && exception.getExceptionType()
                            == ScheduleExceptionType.MODIFIED;

            result.add(TodayScheduleItem.builder()
                    .classId(cc.getId())
                    .scheduleId(schedule.getId())
                    .classCode(cc.getCode())
                    .className(cc.getName())
                    .date(today)
                    .startTime(modified && exception.getStartTime() != null
                            ? exception.getStartTime()
                            : schedule.getStartTime())
                    .endTime(modified && exception.getEndTime() != null
                            ? exception.getEndTime()
                            : schedule.getEndTime())
                    .room(modified && exception.getRoom() != null
                            ? exception.getRoom()
                            : schedule.getRoom())
                    .scheduleType(modified ? "MODIFIED" : "REGULAR")
                    .build());
        }

        // 5. Lấy các buổi học bù diễn ra hôm nay
        List<ScheduleException> makeupSchedules =
                scheduleExceptionRepository
                        .findByCourseClassIdInAndStatusAndOccurrenceDate(
                                classIds,
                                ScheduleExceptionStatus.PUBLISHED,
                                today
                        );

        for (ScheduleException makeup : makeupSchedules) {

            if (makeup.getExceptionType()
                    != ScheduleExceptionType.MAKEUP) {
                continue;
            }

            CourseClass cc = makeup.getCourseClass();

            if (cc == null) {
                continue;
            }

            result.add(TodayScheduleItem.builder()
                    .classId(cc.getId())
                    .scheduleId(makeup.getClassSchedule() != null
                            ? makeup.getClassSchedule().getId()
                            : null)
                    .classCode(cc.getCode())
                    .className(cc.getName())
                    .date(today)
                    .startTime(makeup.getStartTime())
                    .endTime(makeup.getEndTime())
                    .room(makeup.getRoom() != null
                            ? makeup.getRoom()
                            : cc.getRoom())
                    .scheduleType("MAKEUP")
                    .build());
        }

        // 6. Sắp xếp theo giờ bắt đầu
        result.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        return result;
    }

    @Transactional(readOnly = true)
    public SemesterScheduleResponse getSemesterSchedule(UUID userId, Role role) {
        List<UUID> classIds = getClassIdsForUser(userId, role);
        if (classIds.isEmpty()) {
            return SemesterScheduleResponse.builder()
                    .regularSchedules(new ArrayList<>())
                    .scheduleExceptions(new ArrayList<>())
                    .build();
        }

        List<ClassSchedule> schedules = classScheduleRepository.findByCourseClassIdIn(classIds);
        List<ClassScheduleResponse> regularSchedules = schedules.stream()
                .sorted(Comparator
                        .comparing(ClassSchedule::getDayOfWeek, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ClassSchedule::getStartTime, Comparator.nullsLast(LocalTime::compareTo)))
                .map(this::toScheduleResponse)
                .collect(Collectors.toList());

        List<ScheduleException> exceptions = scheduleExceptionRepository
                .findByCourseClassIdInAndStatusOrderByOriginalDateDesc(classIds, ScheduleExceptionStatus.PUBLISHED);
        List<ScheduleExceptionResponse> scheduleExceptions = exceptions.stream()
                .map(this::toExceptionResponse)
                .collect(Collectors.toList());

        return SemesterScheduleResponse.builder()
                .regularSchedules(regularSchedules)
                .scheduleExceptions(scheduleExceptions)
                .build();
    }
}
