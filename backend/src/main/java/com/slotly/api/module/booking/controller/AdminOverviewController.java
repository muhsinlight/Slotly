package com.slotly.api.module.booking.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;

    public AdminOverviewController(AppointmentRepository appointments, WaitlistRepository waitlist) {
        this.appointments = appointments;
        this.waitlist = waitlist;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Overview>> overview() {
        List<AppointmentItem> appointmentItems = appointments.findAllByOrderByStartsAtDesc().stream()
            .map(item -> new AppointmentItem(
                item.getId(),
                item.getPlace().getName(),
                item.getOffering().getName(),
                item.getCustomer().getUsername(),
                item.getStartsAt(),
                item.getEndsAt(),
                item.getStatus().name()
            ))
            .toList();
        List<WaitlistItem> waitlistItems = waitlist.findAllByOrderByCreatedAtDesc().stream()
            .map(item -> new WaitlistItem(
                item.getId(),
                item.getPlace().getName(),
                item.getOffering().getName(),
                item.getCustomer().getUsername(),
                item.getRequestedStartsAt(),
                item.getStatus().name(),
                item.getOfferExpiresAt()
            ))
            .toList();
        return ApiResponse.<Overview>builder()
            .message("Admin overview listed")
            .data(new Overview(appointmentItems, waitlistItems))
            .response();
    }

    public record Overview(List<AppointmentItem> appointments, List<WaitlistItem> waitlist) {}

    public record AppointmentItem(
        UUID id,
        String placeName,
        String offeringName,
        String customerUsername,
        Instant startsAt,
        Instant endsAt,
        String status
    ) {}

    public record WaitlistItem(
        UUID id,
        String placeName,
        String offeringName,
        String customerUsername,
        Instant requestedStartsAt,
        String status,
        Instant offerExpiresAt
    ) {}
}
