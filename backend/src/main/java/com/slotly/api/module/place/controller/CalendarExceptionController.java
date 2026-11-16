package com.slotly.api.module.place.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.place.dto.request.CalendarExceptionRequest;
import com.slotly.api.module.place.dto.response.CalendarExceptionResponse;
import com.slotly.api.module.place.service.BusinessCalendarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/places/{placeId}/calendar-exceptions")
public class CalendarExceptionController {
    private final BusinessCalendarService calendar;

    public CalendarExceptionController(BusinessCalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarExceptionResponse>>> list(@PathVariable UUID placeId, Authentication auth) {
        return ApiResponse.<List<CalendarExceptionResponse>>builder()
            .message("Özel günler listelendi.")
            .data(calendar.list(UUID.fromString(auth.getName()), placeId))
            .response();
    }

    @PutMapping("/{date}")
    public ResponseEntity<ApiResponse<CalendarExceptionResponse>> save(
        @PathVariable UUID placeId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody CalendarExceptionRequest request,
        Authentication auth
    ) {
        return ApiResponse.<CalendarExceptionResponse>builder()
            .message("Özel gün kaydedildi.")
            .data(calendar.save(UUID.fromString(auth.getName()), placeId, date, request))
            .response();
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable UUID placeId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication auth
    ) {
        calendar.delete(UUID.fromString(auth.getName()), placeId, date);
    }
}
