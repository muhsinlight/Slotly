package com.slotly.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlotlyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlotlyApiApplication.class, args);
	}

}
