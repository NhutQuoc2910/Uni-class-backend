package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uit.edulens.entity.ClassSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClassScheduleRepository
        extends JpaRepository<ClassSchedule, UUID> {

    List<ClassSchedule>
    findByCourseClassIdInAndDayOfWeekAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            List<UUID> classIds,
            Integer dayOfWeek,
            LocalDate date1,
            LocalDate date2
    );

    List<ClassSchedule> findByCourseClassId(UUID classId);

    List<ClassSchedule> findByCourseClassIdIn(List<UUID> classIds);
}
