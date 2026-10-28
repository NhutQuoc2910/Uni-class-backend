package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.AttendanceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findBySessionId(UUID sessionId);
    List<AttendanceRecord> findByStudentId(UUID studentId);
    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);
    Optional<AttendanceRecord> findBySessionIdAndDeviceId(UUID sessionId, String deviceId);
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);
    boolean existsBySessionIdAndDeviceId(UUID sessionId, String deviceId);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r " +
           "WHERE r.session.courseClass.id = :classId " +
           "AND r.session.status = vn.edu.uit.edulens.entity.enums.SessionStatus.CLOSED " +
           "AND r.student.id = :studentId " +
           "AND r.status = vn.edu.uit.edulens.entity.enums.AttendanceStatus.PRESENT")
    long countPresentInClosedSessionsByStudent(
            @Param("classId") UUID classId,
            @Param("studentId") UUID studentId
    );

    @Query("SELECT COUNT(r) FROM AttendanceRecord r " +
           "WHERE r.session.courseClass.id = :classId " +
           "AND r.session.status = vn.edu.uit.edulens.entity.enums.SessionStatus.CLOSED " +
           "AND r.status = vn.edu.uit.edulens.entity.enums.AttendanceStatus.PRESENT")
    long countPresentInClosedSessions(@Param("classId") UUID classId);
}
