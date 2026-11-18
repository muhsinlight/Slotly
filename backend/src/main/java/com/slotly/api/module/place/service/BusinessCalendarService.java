package com.slotly.api.module.place.service;

import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.dto.request.CalendarExceptionRequest;
import com.slotly.api.module.place.dto.response.CalendarExceptionResponse;
import com.slotly.api.module.place.entity.CalendarException;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.CalendarExceptionRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessCalendarService {
    private final PlaceRepository places;
    private final BusinessHourRepository hours;
    private final CalendarExceptionRepository exceptions;
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;
    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendarService(
        PlaceRepository places,
        BusinessHourRepository hours,
        CalendarExceptionRepository exceptions,
        AppointmentRepository appointments,
        WaitlistRepository waitlist,
        Clock clock,
        @Value("${slotly.business.time-zone:Europe/Istanbul}") String zone
    ) {
        this.places = places;
        this.hours = hours;
        this.exceptions = exceptions;
        this.appointments = appointments;
        this.waitlist = waitlist;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public record OpeningInterval(LocalTime opensAt, LocalTime closesAt) {
    }

    public ZoneId timeZone() {
        return zone;
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, List<OpeningInterval>> intervals(UUID placeId, LocalDate from, LocalDate to) {
        Map<DayOfWeek, List<OpeningInterval>> weekly = new EnumMap<>(DayOfWeek.class);
        for (var hour : hours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(placeId)) {
            weekly.computeIfAbsent(hour.getDayOfWeek(), ignored -> new ArrayList<>())
                .add(new OpeningInterval(hour.getOpensAt(), hour.getClosesAt()));
        }
        Map<LocalDate, List<OpeningInterval>> result = new HashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.put(date, weekly.getOrDefault(date.getDayOfWeek(), List.of()));
        }
        for (var exception : exceptions.findByPlaceIdAndDateBetween(placeId, from, to)) {
            result.put(exception.getDate(), exceptionIntervals(exception));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OpeningInterval> intervals(UUID placeId, LocalDate date) {
        var exception = exceptions.findByPlaceIdAndDate(placeId, date);
        if (exception.isPresent()) {
            return exceptionIntervals(exception.get());
        }
        return hours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(placeId).stream()
            .filter(hour -> hour.getDayOfWeek() == date.getDayOfWeek())
            .map(hour -> new OpeningInterval(hour.getOpensAt(), hour.getClosesAt()))
            .toList();
    }

    private List<OpeningInterval> exceptionIntervals(CalendarException exception) {
        return exception.isClosed() ? List.of() : List.of(new OpeningInterval(exception.getOpensAt(), exception.getClosesAt()));
    }

    @Transactional(readOnly = true)
    public List<CalendarExceptionResponse> list(UUID ownerId, UUID placeId) {
        requireOwner(ownerId, placeId, false);
        return exceptions.findByPlaceIdOrderByDateAsc(placeId).stream().map(CalendarExceptionResponse::from).toList();
    }

    @Transactional
    public CalendarExceptionResponse save(UUID ownerId, UUID placeId, LocalDate date, CalendarExceptionRequest request) {
        Place place = requireOwner(ownerId, placeId, true);
        if (date.isBefore(LocalDate.now(clock.withZone(zone)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçmiş bir günün çalışma düzeni değiştirilemez.");
        }
        if (request.closed() == null || (request.closed()
            ? request.opensAt() != null || request.closesAt() != null
            : request.opensAt() == null || request.closesAt() == null || !request.opensAt().isBefore(request.closesAt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kapalı gün saat içeremez; açık gün için geçerli açılış ve kapanış gerekir.");
        }
        CalendarException exception = exceptions.findByPlaceIdAndDate(placeId, date)
            .orElseGet(() -> new CalendarException(place, date));
        exception.update(request.closed(), request.opensAt(), request.closesAt());
        exceptions.saveAndFlush(exception);
        validateCommitments(placeId);
        return CalendarExceptionResponse.from(exception);
    }

    @Transactional
    public void delete(UUID ownerId, UUID placeId, LocalDate date) {
        requireOwner(ownerId, placeId, true);
        CalendarException exception = exceptions.findByPlaceIdAndDate(placeId, date)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Özel gün bulunamadı."));
        exceptions.delete(exception);
        exceptions.flush();
        validateCommitments(placeId);
    }

    @Transactional
    public void deleteHour(UUID ownerId, UUID placeId, UUID hourId) {
        requireOwner(ownerId, placeId, true);
        var hour = hours.findById(hourId)
            .filter(value -> value.getPlace().getId().equals(placeId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Çalışma saati bulunamadı."));
        hours.delete(hour);
        hours.flush();
        validateCommitments(placeId);
    }

    private void validateCommitments(UUID placeId) {
        Instant now = clock.instant();
        for (var appointment : appointments.findByPlaceIdAndStatusAndEndsAtGreaterThan(placeId, AppointmentStatus.CONFIRMED, now)) {
            ensureCovered(placeId, appointment.getStartsAt(), appointment.getEndsAt());
        }
        for (var offer : waitlist.findByPlaceIdAndStatusAndOfferExpiresAtGreaterThan(placeId, WaitlistStatus.OFFERED, now)) {
            ensureCovered(placeId, offer.getRequestedStartsAt(), offer.getRequestedEndsAt());
        }
    }

    private void ensureCovered(UUID placeId, Instant start, Instant end) {
        var localStart = start.atZone(zone);
        var localEnd = end.atZone(zone);
        boolean covered = localStart.toLocalDate().equals(localEnd.toLocalDate())
            && intervals(placeId, localStart.toLocalDate()).stream().anyMatch(interval ->
                !localStart.toLocalTime().isBefore(interval.opensAt()) && !localEnd.toLocalTime().isAfter(interval.closesAt()));
        if (!covered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Değişiklik mevcut randevu veya aktif bekleme teklifiyle çakışıyor. Önce bu kayıtları çözümleyin.");
        }
    }

    private Place requireOwner(UUID ownerId, UUID placeId, boolean lock) {
        Place place = (lock ? places.findLockedById(placeId) : places.findById(placeId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mekân bulunamadı."));
        if (!place.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu mekânı yönetemezsiniz.");
        }
        return place;
    }
}
