package com.infominez.ecommerce.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class RewardPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RewardProperties.class)
    static class SettingsConfiguration {}
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SettingsConfiguration.class)
            .withPropertyValues("rewards.n=5", "rewards.x=10");

    @Test void bindsPercentagePointsAndAllowsInclusiveHundred() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RewardProperties.class).n()).isEqualTo(5);
            assertThat(context.getBean(RewardProperties.class).x()).isEqualByComparingTo("10");
        });
        runner.withPropertyValues("rewards.x=100").run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("rewards.x=0.0001").run(context -> assertThat(context).hasNotFailed());
    }

    @Test void refusesInvalidSettingsAtStartup() {
        for (String value : new String[]{"rewards.n=0", "rewards.n=-1", "rewards.x=0", "rewards.x=-1",
                "rewards.x=100.0001", "rewards.x=101", "rewards.x=10.00001", "rewards.x=invalid"}) {
            runner.withPropertyValues(value).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("rewards");
            });
        }
    }
}
