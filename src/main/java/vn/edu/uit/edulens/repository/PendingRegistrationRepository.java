package vn.edu.uit.edulens.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.edulens.entity.PendingRegistration;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingRegistrationRepository
        extends JpaRepository<PendingRegistration, UUID> {

    Optional<PendingRegistration> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);

    void deleteByOtpExpiresAtBefore(Instant time);
}