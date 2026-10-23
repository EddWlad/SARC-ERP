package com.tidsec.sarc_bakend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SarcBakendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SarcBakendApplication.class, args);
	}

}
