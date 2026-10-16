package net.olaba.mvnbuilder.repository;

import net.olaba.mvnbuilder.entities.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository for persisted notifications.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns notifications newest first, bounded by the supplied page size.
     *
     * @param pageable page size limiting the result
     * @return newest notifications
     */
    List<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * Counts notifications that have not been seen in the history panel.
     *
     * @return unread count
     */
    long countByReadFalse();

    /**
     * Identifiers of the most recent notifications, newest first.
     *
     * @param pageable page size limiting the result
     * @return notification identifiers
     */
    @Query("select n.id from Notification n order by n.createdAt desc, n.id desc")
    List<Long> findNewestIds(Pageable pageable);

    /**
     * Deletes every notification whose identifier is not in the retained set.
     *
     * @param ids identifiers to keep
     * @return number of deleted rows
     */
    @Modifying
    @Query("delete from Notification n where n.id not in :ids")
    int deleteNotIn(@Param("ids") Collection<Long> ids);

    /**
     * Marks every unread notification as read.
     *
     * @return number of updated rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.read = false")
    int markAllAsRead();
}
