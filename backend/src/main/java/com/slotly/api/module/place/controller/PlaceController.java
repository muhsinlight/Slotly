package com.slotly.api.module.place.controller;

import com.slotly.api.module.place.dto.request.BusinessHourRequest;
import com.slotly.api.module.place.dto.request.OfferingRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.place.dto.response.BusinessHourResponse;
import com.slotly.api.module.place.dto.response.OfferingResponse;
import com.slotly.api.module.place.dto.response.PlaceResponse;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;

import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PlaceController {
    private final BookingService service;

    public PlaceController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/places")
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> places() {
        return ApiResponse.<List<PlaceResponse>>builder()
            .message("Places listed")
            .data(service.publicPlaces())
            .response();
    }

    @GetMapping("/places/{id}")
    public ResponseEntity<ApiResponse<PlaceResponse>> place(@PathVariable UUID id) {
        return ApiResponse.<PlaceResponse>builder()
            .message("Place found")
            .data(service.publicPlace(id))
            .response();
    }

    @GetMapping("/owner/places")
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> mine(Authentication authentication) {
        return ApiResponse.<List<PlaceResponse>>builder()
            .message("Places listed")
            .data(service.myPlaces(UUID.fromString(authentication.getName())))
            .response();
    }

    @PostMapping("/owner/places")
    public ResponseEntity<ApiResponse<PlaceResponse>> create(@Valid @RequestBody PlaceRequest request, Authentication authentication) {
        return ApiResponse.<PlaceResponse>builder()
            .message("Place created")
            .data(service.createPlace(UUID.fromString(authentication.getName()), request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @PutMapping("/owner/places/{id}")
    public ResponseEntity<ApiResponse<PlaceResponse>> update(@PathVariable UUID id, @Valid @RequestBody PlaceRequest request, Authentication authentication) {
        return ApiResponse.<PlaceResponse>builder()
            .message("Place updated")
            .data(service.updatePlace(UUID.fromString(authentication.getName()), id, request))
            .response();
    }

    @DeleteMapping("/owner/places/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        service.deletePlace(UUID.fromString(authentication.getName()), id);
    }

    @GetMapping("/places/{id}/offerings")
    public ResponseEntity<ApiResponse<List<OfferingResponse>>> offerings(@PathVariable UUID id) {
        return ApiResponse.<List<OfferingResponse>>builder()
            .message("Offerings listed")
            .data(service.publicOfferings(id))
            .response();
    }

    @GetMapping("/owner/places/{placeId}/offerings")
    public ResponseEntity<ApiResponse<List<OfferingResponse>>> ownerOfferings(
        @PathVariable UUID placeId,
        Authentication authentication
    ) {
        return ApiResponse.<List<OfferingResponse>>builder()
            .message("Offerings listed")
            .data(service.ownerOfferings(UUID.fromString(authentication.getName()), placeId))
            .response();
    }

    @PostMapping("/owner/places/{placeId}/offerings")
    public ResponseEntity<ApiResponse<OfferingResponse>> createOffering(@PathVariable UUID placeId, @Valid @RequestBody OfferingRequest request, Authentication authentication) {
        return ApiResponse.<OfferingResponse>builder()
            .message("Offering created")
            .data(service.createOffering(UUID.fromString(authentication.getName()), placeId, request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @PutMapping("/owner/places/{placeId}/offerings/{id}")
    public ResponseEntity<ApiResponse<OfferingResponse>> updateOffering(@PathVariable UUID placeId, @PathVariable UUID id, @Valid @RequestBody OfferingRequest request, Authentication authentication) {
        return ApiResponse.<OfferingResponse>builder()
            .message("Offering updated")
            .data(service.updateOffering(UUID.fromString(authentication.getName()), placeId, id, request))
            .response();
    }

    @DeleteMapping("/owner/places/{placeId}/offerings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffering(@PathVariable UUID placeId, @PathVariable UUID id, Authentication authentication) {
        service.deleteOffering(UUID.fromString(authentication.getName()), placeId, id);
    }

    @GetMapping("/places/{id}/business-hours")
    public ResponseEntity<ApiResponse<List<BusinessHourResponse>>> hours(@PathVariable UUID id) {
        return ApiResponse.<List<BusinessHourResponse>>builder()
            .message("Business hours listed")
            .data(service.getHours(id))
            .response();
    }

    @PostMapping("/owner/places/{placeId}/business-hours")
    public ResponseEntity<ApiResponse<BusinessHourResponse>> addHour(@PathVariable UUID placeId, @Valid @RequestBody BusinessHourRequest request, Authentication authentication) {
        return ApiResponse.<BusinessHourResponse>builder()
            .message("Business hours created")
            .data(service.addHour(UUID.fromString(authentication.getName()), placeId, request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @DeleteMapping("/owner/places/{placeId}/business-hours/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHour(@PathVariable UUID placeId, @PathVariable UUID id, Authentication authentication) {
        service.deleteHour(UUID.fromString(authentication.getName()), placeId, id);
    }

    @GetMapping("/places/{id}/availability")
    public ResponseEntity<ApiResponse<List<Instant>>> availability(@PathVariable UUID id, @RequestParam UUID offeringId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.<List<Instant>>builder()
            .message("Availability listed")
            .data(service.availability(id, offeringId, from, to))
            .response();
    }
}
