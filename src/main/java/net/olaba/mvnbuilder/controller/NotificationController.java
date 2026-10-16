package net.olaba.mvnbuilder.controller;

import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.model.LocalNotificationRequest;
import net.olaba.mvnbuilder.model.NotificationDto;
import net.olaba.mvnbuilder.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * HTTP API for the persisted notification history and unread badge.
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lists stored notifications, newest first.
     *
     * @return notification history
     */
    @GetMapping
    @ResponseBody
    public List<NotificationDto> list() {
        return notificationService.recent();
    }

    /**
     * Returns the number of unread notifications.
     *
     * @return unread count payload
     */
    @GetMapping("/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    /**
     * Records a client-originated local success or error message.
     *
     * @param request the local notification payload
     * @return the persisted notification
     */
    @PostMapping("/local")
    @ResponseBody
    public ResponseEntity<NotificationDto> recordLocal(final @RequestBody LocalNotificationRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A notification message is required.");
        }
        return ResponseEntity.ok(notificationService.toDto(notificationService.recordLocal(request)));
    }

    /**
     * Marks every notification as read.
     *
     * @return an acknowledgement
     */
    @PostMapping("/read")
    @ResponseBody
    public ResponseEntity<String> markRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok("OK");
    }

    /**
     * Deletes the entire notification history.
     *
     * @return an acknowledgement
     */
    @DeleteMapping
    @ResponseBody
    public ResponseEntity<String> clear() {
        notificationService.clear();
        return ResponseEntity.ok("OK");
    }
}
