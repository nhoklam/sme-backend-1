package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Notification;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByUserIdIsNullAndIsReadFalseOrderByCreatedAtDesc();

    long countByUserIdAndIsReadFalse(UUID userId);

    long countByUserIdIsNullAndIsReadFalse();
}
