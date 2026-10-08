package com.bankalmurqarmah.switchapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Boot entry for standalone JAR/WAR and external Tomcat.
 * Callers: java -jar target/bml-switch.war OR Tomcat 10.1+ webapps.
 * User: bank Windows has Docker/WSL blocked — deploy on Tomcat + run DB scripts.
 */
@SpringBootApplication
public class SwitchApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SwitchApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SwitchApplication.class, args);
    }
}
