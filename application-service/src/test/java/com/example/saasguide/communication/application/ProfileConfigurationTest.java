package com.example.saasguide.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.saasguide.communication.application.config.EligibilityClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProfileConfigurationTest {
    @Test
    void applicationAndMeshProfilesUseDifferentReadTimeouts() throws Exception {
        assertThat(load("application-application-timeout.yml").readTimeout().getSeconds()).isEqualTo(2);
        assertThat(load("application-mesh-timeout.yml").readTimeout().getSeconds()).isEqualTo(10);
    }

    private EligibilityClientProperties load(String name) throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load(name, new ClassPathResource(name))) {
            sources.addLast(source);
        }
        // Profile files override the defaults that supply base URL and connect timeout.
        sources.addLast(new org.springframework.core.env.MapPropertySource("defaults", java.util.Map.of(
                "clients.eligibility.base-url", "http://localhost",
                "clients.eligibility.connect-timeout", "1s")));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("clients.eligibility", EligibilityClientProperties.class)
                .orElseThrow();
    }
}
