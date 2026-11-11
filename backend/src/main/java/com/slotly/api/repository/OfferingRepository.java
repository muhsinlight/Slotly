package com.slotly.api.repository;
import com.slotly.api.entity.Offering;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OfferingRepository extends JpaRepository<Offering, UUID> {
    List<Offering> findByPlaceIdAndActiveTrue(UUID placeId);
}
