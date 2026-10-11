package com.premiumfinance.unpaids.extractor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {

    /** All "today" and business-date logic goes through this clock, so tests can fix it. */
    @Bean
    public Clock clock(ExtractorProperties properties) {
        return Clock.system(properties.businessZone());
    }

    /** Mapper for the event contract, independent of any Jackson auto-configuration. */
    @Bean
    public JsonMapper eventJsonMapper() {
        return JsonMapper.builder().build();
    }
}
