package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uit.edulens.entity.ScheduleException;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionStatus;
import vn.edu.uit.edulens.entity.enums.ScheduleExceptionType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleExceptionRepository
        extends JpaRepository<ScheduleException, UUID> {

    List<ScheduleException>
    findByCourseClassIdInAndStatusAndOriginalDate(
            List<UUID> classIds,
            ScheduleExceptionStatus status,
            LocalDate originalDate
    );

    List<ScheduleException>
    findByCourseClassIdInAndStatusAndOccurrenceDate(
            List<UUID> classIds,
            ScheduleExceptionStatus status,
            LocalDate occurrenceDate
    );

    boolean existsByClassScheduleIdAndOriginalDateAndExceptionTypeAndStatusNot(
            UUID scheduleId,
            LocalDate originalDate,
            ScheduleExceptionType type,
            ScheduleExceptionStatus status
    );

    List<ScheduleException> findByCourseClassIdOrderByOriginalDateDesc(UUID classId);

    List<ScheduleException> findByCourseClassIdInAndStatusOrderByOriginalDateDesc(List<UUID> classIds, ScheduleExceptionStatus status);

    List<ScheduleException> findByClassScheduleId(UUID scheduleId);

    List<ScheduleException> findByRelatedExceptionId(UUID relatedExceptionId);

    void deleteByClassScheduleId(UUID scheduleId);
}
