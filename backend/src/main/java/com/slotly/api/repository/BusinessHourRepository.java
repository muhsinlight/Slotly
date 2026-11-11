package com.slotly.api.repository;
import com.slotly.api.entity.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BusinessHourRepository extends JpaRepository<BusinessHour, UUID> {
    List<BusinessHour> findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(UUID placeId);
}
