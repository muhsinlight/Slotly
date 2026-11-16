package com.slotly.api.module.place.dto.response;

import com.slotly.api.module.place.entity.CalendarException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CalendarExceptionResponse(UUID id, LocalDate date, boolean closed, LocalTime opensAt, LocalTime closesAt) {
    public static CalendarExceptionResponse from(CalendarException exception) {
        return new CalendarExceptionResponse(
            exception.getId(), exception.getDate(), exception.isClosed(), exception.getOpensAt(), exception.getClosesAt()
        );
    }
}
