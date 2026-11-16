package ma.sanai3i;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Sanai3iApplication {
    public static void main(String[] args) {
        SpringApplication.run(Sanai3iApplication.class, args);
    }
}
