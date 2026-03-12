package com.MovieRecSys.Movie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://localhost:27017/test",
		"spring.mongodb.database=movie-api-db-test",
		"app.catalog.seed-on-startup=false",
		"app.recommendations.projector.enabled=false"
})
class MovieApplicationTests {

	@Test
	void contextLoads() {
	}

}
