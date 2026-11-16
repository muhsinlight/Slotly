package com.slotly.api.module.place.repository;
import com.slotly.api.module.place.entity.Place;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PlaceRepository extends JpaRepository<Place,UUID> {
    List<Place> findByActiveTrue();

    List<Place> findByOwnerId(UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id")
    Optional<Place> findLockedById(@Param("id") UUID id);
}
