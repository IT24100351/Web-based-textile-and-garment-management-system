package lk.ac.sliit.tgms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TgmsApplication {

    static {
        // Some shells/tools export a generic DEBUG variable. Spring Boot treats that as its own
        // debug switch, which can expose request DTOs in framework logs. TGMS debugging must be
        // requested explicitly with TGMS_DEBUG=true, -Ddebug=true, or -Dtgms.debug=true.
        if (System.getProperty("debug") == null) {
            System.setProperty(
                    "debug",
                    System.getProperty(
                            "tgms.debug", System.getenv().getOrDefault("TGMS_DEBUG", "false")));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TgmsApplication.class, args);
    }
}
