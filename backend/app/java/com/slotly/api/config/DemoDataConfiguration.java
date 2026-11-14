package com.slotly.api.config;

import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Component
@Profile("demo")
public class DemoDataConfiguration implements ApplicationRunner {
    private final UserRepository users;
    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final BusinessHourRepository hours;
    private final PasswordEncoder passwords;
    private final DataSource dataSource;
    private final Environment environment;

    public DemoDataConfiguration(
        UserRepository users,
        PlaceRepository places,
        OfferingRepository offerings,
        BusinessHourRepository hours,
        PasswordEncoder passwords,
        DataSource dataSource,
        Environment environment
    ) {
        this.users = users;
        this.places = places;
        this.offerings = offerings;
        this.hours = hours;
        this.passwords = passwords;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("Demo and production profiles cannot be combined");
        }
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().endsWith("/slotly_demo")) {
                throw new IllegalStateException("Demo seed requires the isolated slotly_demo database");
            }
        }

        User owner = account("demo-owner", "Deniz", UserRole.OWNER);
        account("demo-customer", "Ada", UserRole.CUSTOMER);
        if (!places.findByOwnerId(owner.getId()).isEmpty()) {
            return;
        }
        Place place = places.save(new Place(
            owner,
            "Slotly Demo Stüdyo",
            "Örnek verilerle randevu ve bekleme listesi denemesi.",
            "Demo Mahallesi, Örnek Sokak 1",
            1
        ));
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.save(new BusinessHour(place, day, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
        offerings.save(new Offering(
            place, "Kısa bakım", "Örnek hizmet", new BigDecimal("250.00"), 30, OfferingKind.SERVICE
        ));
        offerings.save(new Offering(
            place, "Kapsamlı bakım", "Örnek hizmet", new BigDecimal("450.00"), 60, OfferingKind.SERVICE
        ));
    }

    private User account(String username, String firstName, UserRole role) {
        return users.findByUsername(username).orElseGet(() -> users.save(new User(
            username,
            passwords.encode("SlotlyDemo2026!"),
            firstName,
            "Demo",
            role
        )));
    }
}
