package com.smartclinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * Medical operations admin application.
 */
@SpringBootApplication
public class MedicalAdminApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MedicalAdminApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("Medical operations admin started.");
    }

}
