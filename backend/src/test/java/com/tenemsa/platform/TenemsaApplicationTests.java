package com.tenemsa.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// "test" profile = in-memory H2 and dummy settings (application-test.properties),
// so the context loads without a real database or a .env file.
@SpringBootTest
@ActiveProfiles("test")
class TenemsaApplicationTests {

	@Test
	void contextLoads() {
	}

}
