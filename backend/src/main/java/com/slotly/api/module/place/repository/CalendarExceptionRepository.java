package com.slotly.api.module.place.repository;

import com.slotly.api.module.place.entity.CalendarException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarExceptionRepository extends JpaRepository<CalendarException, UUID> {
    Optional<CalendarException> findByPlaceIdAndDate(UUID placeId, LocalDate date);

    List<CalendarException> findByPlaceIdOrderByDateAsc(UUID placeId);

    List<CalendarException> findByPlaceIdAndDateBetween(UUID placeId, LocalDate from, LocalDate to);
}
