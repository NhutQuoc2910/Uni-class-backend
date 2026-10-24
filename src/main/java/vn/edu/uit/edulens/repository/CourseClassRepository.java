package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.CourseClass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseClassRepository extends JpaRepository<CourseClass, UUID> {
    Optional<CourseClass> findByInviteCode(String inviteCode);
    List<CourseClass> findByLecturerId(UUID lecturerId);
    boolean existsByInviteCode(String inviteCode);
}
