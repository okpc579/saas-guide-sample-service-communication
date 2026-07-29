package com.example.saasguide.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.saasguide.communication.application.context.RequestContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ApplicationServiceApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithApplicationAndFrameworkRequestContextFilters() {
        assertThat(applicationContext.getBean("applicationRequestContextFilter"))
                .isInstanceOf(RequestContextFilter.class);
        assertThat(applicationContext.containsBean("requestContextFilter")).isTrue();
    }
}
