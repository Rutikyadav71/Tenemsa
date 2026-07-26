package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying
    @Query("UPDATE Notification n SET n.read = true")
    void markAllRead();
}
