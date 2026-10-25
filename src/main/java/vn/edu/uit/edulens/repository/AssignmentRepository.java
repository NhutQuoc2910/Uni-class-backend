package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.Assignment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByCourseClassIdOrderByDeadlineAsc(UUID classId);
    List<Assignment> findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(UUID classId, Instant now);
    long countByCourseClassIdAndDeadlineAfter(UUID classId, Instant now);
}
