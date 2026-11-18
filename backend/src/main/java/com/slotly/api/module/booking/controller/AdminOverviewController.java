package com.slotly.api.module.booking.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;
    private final Clock clock;

    public AdminOverviewController(AppointmentRepository appointments, WaitlistRepository waitlist, Clock clock) {
        this.appointments = appointments;
        this.waitlist = waitlist;
        this.clock = clock;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Overview>> overview(
        @RequestParam(defaultValue = "0") int appointmentPage,
        @RequestParam(defaultValue = "0") int waitlistPage,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "") String q
    ) {
        if (size < 1 || size > 100 || appointmentPage < 0 || waitlistPage < 0
            || (long) Math.max(appointmentPage, waitlistPage) * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page number or page size");
        }
        String term = q.strip().toLowerCase(Locale.forLanguageTag("tr"));
        var booked = appointments.adminPage(term, PageRequest.of(appointmentPage, size));
        var queued = waitlist.adminPage(term, PageRequest.of(waitlistPage, size));
        Instant now = clock.instant();
        List<AppointmentItem> appointmentItems = booked.stream()
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
        List<WaitlistItem> waitlistItems = queued.stream()
            .map(item -> new WaitlistItem(
                item.getId(),
                item.getPlace().getName(),
                item.getOffering().getName(),
                item.getCustomer().getUsername(),
                item.getRequestedStartsAt(),
                item.isDue(now) ? WaitlistStatus.EXPIRED.name() : item.getStatus().name(),
                item.getOfferExpiresAt()
            ))
            .toList();
        return ApiResponse.<Overview>builder()
            .message("Admin overview listed")
            .data(new Overview(
                appointmentItems,
                waitlistItems,
                booked.getTotalElements(),
                queued.getTotalElements(),
                appointments.countByStatusAndStartsAtGreaterThanEqual(AppointmentStatus.CONFIRMED, now),
                waitlist.countByStatusAndRequestedStartsAtGreaterThan(WaitlistStatus.WAITING, now),
                waitlist.countByStatusAndOfferExpiresAtGreaterThan(WaitlistStatus.OFFERED, now)
            ))
            .response();
    }

    public record Overview(
        List<AppointmentItem> appointments,
        List<WaitlistItem> waitlist,
        long appointmentTotal,
        long waitlistTotal,
        long upcoming,
        long waiting,
        long offers
    ) {}

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
