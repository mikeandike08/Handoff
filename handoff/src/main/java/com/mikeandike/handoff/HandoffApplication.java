package com.mikeandike.handoff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HandoffApplication {

	public static void main(String[] args) {
		SpringApplication.run(HandoffApplication.class, args);
	}

}
