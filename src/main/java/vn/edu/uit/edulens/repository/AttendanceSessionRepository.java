package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.AttendanceSession;
import vn.edu.uit.edulens.entity.enums.SessionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {
    List<AttendanceSession> findByCourseClassIdOrderByCreatedAtDesc(UUID classId);
    Optional<AttendanceSession> findFirstByCourseClassIdAndStatus(UUID classId, SessionStatus status);
    Optional<AttendanceSession> findFirstByStatusOrderByCreatedAtDesc(SessionStatus status);
    long countByCourseClassIdAndStatus(UUID classId, SessionStatus status);
}
