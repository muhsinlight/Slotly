package com.slotly.api.module.place.service;

import com.slotly.api.module.place.dto.request.BusinessHourRequest;
import com.slotly.api.module.place.dto.request.OfferingRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.place.dto.response.BusinessHourResponse;
import com.slotly.api.module.place.dto.response.OfferingResponse;
import com.slotly.api.module.place.dto.response.PlaceResponse;

import java.util.List;
import java.util.UUID;

public interface PlaceService {
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
}
