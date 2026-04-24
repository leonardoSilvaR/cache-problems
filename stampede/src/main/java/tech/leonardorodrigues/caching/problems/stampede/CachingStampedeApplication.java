package tech.leonardorodrigues.caching.problems.stampede;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CachingStampedeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CachingStampedeApplication.class, args);
	}

}
