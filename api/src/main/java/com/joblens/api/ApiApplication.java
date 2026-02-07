package com.joblens.api;

import io.github.cdimascio.dotenv.Dotenv;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ApiApplication {

	public static void main(String[] args) {
		Path envInCwd = Paths.get(System.getProperty("user.dir"), ".env");
		Path envInApi = Paths.get(System.getProperty("user.dir"), "api", ".env");
		Path envDir = Files.exists(envInCwd) ? envInCwd.getParent() : envInApi.getParent();

		Dotenv.configure()
				.directory(envDir.toString())
				.ignoreIfMissing()
				.systemProperties()
				.load();

		SpringApplication.run(ApiApplication.class, args);
	}

}

