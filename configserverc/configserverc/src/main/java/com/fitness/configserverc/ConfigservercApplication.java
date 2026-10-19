package com.fitness.configserverc;

import org.springframework.boot.SpringApplication;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;


@SpringBootApplication
@EnableConfigServer
public class ConfigservercApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigservercApplication.class, args);
	}

}
