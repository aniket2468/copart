package com.acme.ems.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
  @Bean
  Clock businessClock(@Value("${app.business-zone}") String businessZone) {
    return Clock.system(ZoneId.of(businessZone));
  }
}
