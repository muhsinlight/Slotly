package com.slotly.api.module.place.repository;
import com.slotly.api.module.place.entity.Offering;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OfferingRepository extends JpaRepository<Offering, UUID> {
    List<Offering> findByPlaceIdAndActiveTrue(UUID placeId);

    List<Offering> findByPlaceIdOrderByNameAsc(UUID placeId);
}
