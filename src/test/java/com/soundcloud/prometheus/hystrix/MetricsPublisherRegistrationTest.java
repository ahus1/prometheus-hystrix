package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherDefault;
import io.prometheus.client.CollectorRegistry;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Alexander Schwartz 2017
 */
public class MetricsPublisherRegistrationTest {

    @After
    public void teardown() {
        Hystrix.reset();
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    public void shouldRegisterDespitePreviouslyRegisteredHystrixMetricsPlugins() {
        // given
        // ... a provider is already registered
        HystrixPlugins.getInstance().registerMetricsPublisher(HystrixMetricsPublisherDefault.getInstance());

        // when
        // ... we register (without throwing a "Another strategy was already registered" exception)
        HystrixPrometheusMetricsPublisher.register("exampleapp");

        // then
        // ... we'll be able to collect metrics for commands
        TestHystrixCommand command = new TestHystrixCommand("any");
        command.execute();
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_event_total",
                        new String[]{"command_group", "command_name", "event"},
                        new String[]{"group_any",
                                "command_any", "success"}))
                .describedAs("counter is present")
                .isGreaterThan(0);
    }

}
