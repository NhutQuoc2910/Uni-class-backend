package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.Submission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findByAssignmentId(UUID assignmentId);
    Optional<Submission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
    boolean existsByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
}
