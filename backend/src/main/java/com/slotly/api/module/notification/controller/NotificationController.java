package com.slotly.api.module.notification.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationService.Preferences>> preferences(Authentication authentication) {
        return ApiResponse.<NotificationService.Preferences>builder().message("Bildirim tercihleri")
            .data(service.preferences(UUID.fromString(authentication.getName()))).response();
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> save(Authentication authentication, @RequestBody NotificationService.Preferences preferences) {
        service.savePreferences(UUID.fromString(authentication.getName()), preferences);
        return ApiResponse.<Void>builder().message("Tercihler kaydedildi.").response();
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<List<NotificationService.Delivery>>> deliveries(Authentication authentication) {
        return ApiResponse.<List<NotificationService.Delivery>>builder().message("Bildirim geçmişi")
            .data(service.deliveries(UUID.fromString(authentication.getName()))).response();
    }
}
