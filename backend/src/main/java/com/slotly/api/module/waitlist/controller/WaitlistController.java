package com.slotly.api.module.waitlist.controller;

import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.waitlist.service.WaitlistNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {
    private final BookingService bookingService;
    private final WaitlistNotificationService notifications;

    public WaitlistController(BookingService bookingService, WaitlistNotificationService notifications) {
        this.bookingService = bookingService;
        this.notifications = notifications;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication authentication) {
        return notifications.subscribe(UUID.fromString(authentication.getName()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<WaitlistResponse>>> mine(Authentication authentication) {
        return ApiResponse.<List<WaitlistResponse>>builder()
            .message("Waitlist entries listed")
            .data(bookingService.myWaitlist(UUID.fromString(authentication.getName())))
            .response();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WaitlistResponse>> join(@Valid @RequestBody WaitlistRequest request, Authentication authentication) {
        return ApiResponse.<WaitlistResponse>builder()
            .message("Joined the waitlist")
            .data(bookingService.joinWaitlist(UUID.fromString(authentication.getName()), request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<AppointmentResponse>> accept(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.<AppointmentResponse>builder()
            .message("Waitlist offer accepted")
            .data(bookingService.acceptWaitlistOffer(UUID.fromString(authentication.getName()), id))
            .response();
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<ApiResponse<WaitlistResponse>> decline(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.<WaitlistResponse>builder()
            .message("Waitlist offer declined")
            .data(bookingService.declineWaitlistOffer(UUID.fromString(authentication.getName()), id))
            .response();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID id, Authentication authentication) {
        bookingService.leaveWaitlist(UUID.fromString(authentication.getName()), id);
    }
}
