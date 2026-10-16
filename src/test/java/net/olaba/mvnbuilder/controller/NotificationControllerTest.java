package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.Notification;
import net.olaba.mvnbuilder.entities.NotificationCategory;
import net.olaba.mvnbuilder.model.NotificationDto;
import net.olaba.mvnbuilder.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract checks for the notification history endpoints. */
class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private MockMvc mvc;

    /** Builds a standalone MockMvc over the controller with a mocked service. */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService)).build();
    }

    /** The list endpoint returns the mapped history. */
    @Test
    void listReturnsHistory() throws Exception {
        when(notificationService.recent()).thenReturn(List.of(dto()));

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Build result"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    /** The unread-count endpoint reports the count. */
    @Test
    void unreadCountReturnsCount() throws Exception {
        when(notificationService.unreadCount()).thenReturn(3L);

        mvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    /** Valid local messages are persisted and returned. */
    @Test
    void recordLocalPersistsValidMessage() throws Exception {
        when(notificationService.recordLocal(any())).thenReturn(new Notification());
        when(notificationService.toDto(any())).thenReturn(dto());

        mvc.perform(post("/notifications/local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"error\",\"message\":\"boom\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Build result"));

        verify(notificationService).recordLocal(any());
    }

    /** A local message without text is rejected. */
    @Test
    void recordLocalRejectsMalformedMessage() throws Exception {
        mvc.perform(post("/notifications/local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"error\",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).recordLocal(any());
    }

    /** Marking read delegates to the service. */
    @Test
    void markReadDelegates() throws Exception {
        mvc.perform(post("/notifications/read")).andExpect(status().isOk());
        verify(notificationService).markAllRead();
    }

    /** Clearing delegates to the service. */
    @Test
    void clearDelegates() throws Exception {
        mvc.perform(delete("/notifications")).andExpect(status().isOk());
        verify(notificationService).clear();
    }

    private NotificationDto dto() {
        return new NotificationDto(1L, "2026-10-16T10:00:00Z", NotificationCategory.ACTION, "build", true,
                "Build result", "Build completed.", 5L, "Platform",
                List.of(), List.of("module"), List.of(), List.of(), false);
    }
}
