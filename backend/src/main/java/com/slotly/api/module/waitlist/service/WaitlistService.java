package com.slotly.api.module.waitlist.service;

import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;

import java.util.List;
import java.util.UUID;

public interface WaitlistService {
    WaitlistResponse joinWaitlist(UUID customerId, WaitlistRequest request);
    List<WaitlistResponse> myWaitlist(UUID customerId);
    AppointmentResponse acceptWaitlistOffer(UUID customerId, UUID entryId);
    WaitlistResponse declineWaitlistOffer(UUID customerId, UUID entryId);
    void leaveWaitlist(UUID customerId, UUID entryId);
    void expireOffers();
}
