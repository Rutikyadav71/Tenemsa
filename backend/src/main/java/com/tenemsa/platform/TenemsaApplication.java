package com.tenemsa.platform;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TenemsaApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(TenemsaApplication.class, args);
	}

	/**
	 * Copies backend/.env into JVM system properties BEFORE Spring starts, so
	 * ${DB_HOST}, ${JWT_SECRET}, ${SPRING_PROFILES_ACTIVE} etc. resolve no
	 * matter how the app is launched (IDE, ./mvnw, java -jar).
	 *
	 * - A missing .env is fine (production uses real environment variables).
	 * - Real environment variables and -D flags always win over .env.
	 * - Also looks in ./backend, for runs started from the repository root.
	 */
	private static void loadDotEnv() {
		for (String dir : new String[] {".", "./backend"}) {
			Dotenv dotenv = Dotenv.configure().directory(dir).ignoreIfMissing().load();
			if (dotenv.entries().isEmpty()) continue;
			dotenv.entries().forEach(e -> {
				if (System.getenv(e.getKey()) == null && System.getProperty(e.getKey()) == null) {
					System.setProperty(e.getKey(), e.getValue());
				}
			});
			return;
		}
	}

}
