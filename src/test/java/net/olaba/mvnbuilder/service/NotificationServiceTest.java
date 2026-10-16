package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.NotificationCategory;
import net.olaba.mvnbuilder.model.ActionSummary;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LocalNotificationRequest;
import net.olaba.mvnbuilder.model.NotificationDto;
import net.olaba.mvnbuilder.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** Verifies persistence, retention, read state and clearing of notifications. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(NotificationService.class)
class NotificationServiceTest {

    @Autowired private NotificationService service;
    @Autowired private NotificationRepository repository;
    @MockBean private SimpMessagingTemplate messagingTemplate;

    /** Action summaries are persisted with their project lists and broadcast. */
    @Test
    void actionSummaryIsPersistedWithListsAndBroadcast() {
        final ActionSummary summary = ActionSummary.builder()
                .withAction("git-pull")
                .withSuccess(false)
                .withFailedProjects(List.of("failed-module"))
                .withChangedProjects(List.of("updated-module"))
                .withNoChangesProjects(List.of("current-module"))
                .build();

        service.publish(summary, 5L, "Platform");

        verify(messagingTemplate).convertAndSend("/topic/action-summary", summary);
        final List<NotificationDto> all = service.recent();
        assertEquals(1, all.size());
        final NotificationDto dto = all.get(0);
        assertEquals(NotificationCategory.ACTION, dto.category());
        assertEquals("git-pull", dto.action());
        assertFalse(dto.success());
        assertEquals(List.of("failed-module"), dto.failedProjects());
        assertEquals(List.of("updated-module"), dto.changedProjects());
        assertEquals(List.of("current-module"), dto.noChangesProjects());
        assertEquals(5L, dto.workspaceId());
        assertEquals("Platform", dto.workspaceName());
        assertFalse(dto.read());
    }

    /** Build failures are persisted and broadcast over their own topic. */
    @Test
    void buildFailureIsPersistedAndBroadcast() {
        service.publishBuildFailure(new BuildFailure("broken-module", 9L, List.of(9L, 10L)), 5L, "Platform");

        verify(messagingTemplate).convertAndSend(eq("/topic/build-failure"), any(BuildFailure.class));
        final NotificationDto dto = service.recent().get(0);
        assertEquals(NotificationCategory.BUILD_FAILURE, dto.category());
        assertFalse(dto.success());
        assertEquals("Build failed", dto.title());
        assertEquals(List.of("broken-module"), dto.failedProjects());
    }

    /** Local messages are stored with a derived title and correct severity. */
    @Test
    void localMessageIsPersistedWithSeverity() {
        service.recordLocal(new LocalNotificationRequest("error", null, "Could not start the action", 5L, "Platform"));

        final NotificationDto dto = service.recent().get(0);
        assertEquals(NotificationCategory.LOCAL, dto.category());
        assertFalse(dto.success());
        assertEquals("Action failed", dto.title());
        assertEquals("Could not start the action", dto.message());
    }

    /** Unread count starts full and reaches zero after marking all read. */
    @Test
    void unreadCountAndMarkAllRead() {
        service.recordLocal(new LocalNotificationRequest("success", "A", "one", null, null));
        service.recordLocal(new LocalNotificationRequest("success", "B", "two", null, null));
        repository.flush();
        assertEquals(2L, service.unreadCount());

        service.markAllRead();
        repository.flush();

        assertEquals(0L, service.unreadCount());
        assertTrue(service.recent().stream().allMatch(NotificationDto::read));
    }

    /** Clearing removes every stored notification. */
    @Test
    void clearRemovesAllNotifications() {
        service.recordLocal(new LocalNotificationRequest("success", "A", "one", null, null));
        service.recordLocal(new LocalNotificationRequest("success", "B", "two", null, null));

        service.clear();

        assertEquals(0, service.recent().size());
        assertEquals(0L, service.unreadCount());
    }

    /** Retention discards the oldest notifications beyond the configured maximum. */
    @Test
    void retentionPrunesOldestNotifications() {
        final int overflow = NotificationService.MAX_HISTORY + 5;
        for (int index = 0; index < overflow; index++) {
            service.recordLocal(new LocalNotificationRequest("success", "T", "message-" + index, null, null));
        }

        final List<NotificationDto> recent = service.recent();
        assertEquals(NotificationService.MAX_HISTORY, recent.size());
        assertEquals(NotificationService.MAX_HISTORY, repository.count());
        assertEquals("message-" + (overflow - 1), recent.get(0).message());
        assertTrue(recent.stream().noneMatch(dto -> "message-0".equals(dto.message())));
    }
}
