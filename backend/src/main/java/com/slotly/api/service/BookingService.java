package com.slotly.api.service;

import com.slotly.api.dto.request.*;
import com.slotly.api.dto.response.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingService {
    List<PlaceResponse> publicPlaces();
    PlaceResponse publicPlace(UUID id);
    List<PlaceResponse> myPlaces(UUID ownerId);
    PlaceResponse createPlace(UUID ownerId, PlaceRequest request);
    PlaceResponse updatePlace(UUID ownerId, UUID placeId, PlaceRequest request);
    void deletePlace(UUID ownerId, UUID placeId);
    List<OfferingResponse> publicOfferings(UUID placeId);
    List<OfferingResponse> ownerOfferings(UUID ownerId, UUID placeId);
    OfferingResponse createOffering(UUID ownerId, UUID placeId, OfferingRequest request);
    OfferingResponse updateOffering(UUID ownerId, UUID placeId, UUID offeringId, OfferingRequest request);
    void deleteOffering(UUID ownerId, UUID placeId, UUID offeringId);
    List<BusinessHourResponse> getHours(UUID placeId);
    BusinessHourResponse addHour(UUID ownerId, UUID placeId, BusinessHourRequest request);
    void deleteHour(UUID ownerId, UUID placeId, UUID hourId);
    List<Instant> availability(UUID placeId, UUID offeringId, Instant from, Instant to);
    AppointmentResponse createAppointment(UUID customerId, AppointmentRequest request);
    List<AppointmentResponse> myAppointments(UUID customerId);
    void cancel(UUID callerId, UUID appointmentId);
    List<AppointmentResponse> ownerAppointments(UUID ownerId, UUID placeId, Instant from, Instant to);
    WaitlistResponse joinWaitlist(UUID customerId, WaitlistRequest request);
    List<WaitlistResponse> myWaitlist(UUID customerId);
    AppointmentResponse acceptWaitlistOffer(UUID customerId, UUID entryId);
    WaitlistResponse declineWaitlistOffer(UUID customerId, UUID entryId);
    void leaveWaitlist(UUID customerId, UUID entryId);
    void expireOffers();
}
