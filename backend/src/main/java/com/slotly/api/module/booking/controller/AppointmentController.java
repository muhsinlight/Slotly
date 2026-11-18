package com.slotly.api.module.booking.controller;

import com.slotly.api.module.booking.dto.request.AppointmentOutcomeRequest;
import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AppointmentController {
    private final BookingService bookingService;

    public AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(@Valid @RequestBody AppointmentRequest request, Authentication authentication) {
        return ApiResponse.<AppointmentResponse>builder()
            .message("Appointment created")
            .data(bookingService.createAppointment(UUID.fromString(authentication.getName()), request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @GetMapping("/appointments/me")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> mine(Authentication authentication) {
        return ApiResponse.<List<AppointmentResponse>>builder()
            .message("Appointments listed")
            .data(bookingService.myAppointments(UUID.fromString(authentication.getName())))
            .response();
    }

    @DeleteMapping("/appointments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, Authentication authentication) {
        bookingService.cancel(UUID.fromString(authentication.getName()), id);
    }

    @PatchMapping("/owner/appointments/{id}/outcome")
    public ResponseEntity<ApiResponse<AppointmentResponse>> recordOutcome(
        @PathVariable UUID id,
        @Valid @RequestBody AppointmentOutcomeRequest request,
        Authentication authentication
    ) {
        return ApiResponse.<AppointmentResponse>builder()
            .message("Appointment outcome recorded")
            .data(bookingService.recordOutcome(UUID.fromString(authentication.getName()), id, request.status()))
            .response();
    }

    @GetMapping("/owner/appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> owner(@RequestParam UUID placeId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, Authentication authentication) {
        return ApiResponse.<List<AppointmentResponse>>builder()
            .message("Appointments listed")
            .data(bookingService.ownerAppointments(UUID.fromString(authentication.getName()), placeId, from, to))
            .response();
    }
}
