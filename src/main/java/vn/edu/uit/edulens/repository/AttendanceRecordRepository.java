package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);
    boolean existsBySessionIdAndDeviceId(UUID sessionId, String deviceId);
}
