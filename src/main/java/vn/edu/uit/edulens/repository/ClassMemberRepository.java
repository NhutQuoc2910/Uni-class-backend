package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.ClassMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassMemberRepository extends JpaRepository<ClassMember, UUID> {
    List<ClassMember> findByCourseClassId(UUID classId);
    List<ClassMember> findByStudentId(UUID studentId);
    Optional<ClassMember> findByCourseClassIdAndStudentId(UUID classId, UUID studentId);
    boolean existsByCourseClassIdAndStudentId(UUID classId, UUID studentId);
    long countByCourseClassId(UUID classId);
}
