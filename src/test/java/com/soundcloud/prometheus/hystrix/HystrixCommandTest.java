package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.prometheus.client.CollectorRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Alexander Schwartz 2017
 */
public class HystrixCommandTest {

    @Before
    public void setup() {
        HystrixPrometheusMetricsPublisher.register("exampleapp");
    }

    @After
    public void teardown() {
        CollectorRegistry.defaultRegistry.clear();
        Hystrix.reset();
    }

    @Test
    public void shouldIncrementCounterOnSuccecssfulCommand() {
        // given
        TestHystrixCommand command = new TestHystrixCommand("shouldIncrementCounterOnSuccecssfulCommand");

        // when
        command.execute();

        // then
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_event_total",
                        new String[]{"command_group", "command_name", "event"},
                        new String[]{"group_shouldIncrementCounterOnSuccecssfulCommand",
                                "command_shouldIncrementCounterOnSuccecssfulCommand", "success"}))
                .describedAs("counter of successful executions")
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementCounterHistogram() {
        // given
        TestHystrixCommand command = new TestHystrixCommand("shouldIncrementCounterHistogram");

        // when
        command.execute();

        // then
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_latency_execute_seconds_count",
                        new String[]{"command_group", "command_name"},
                        new String[]{"group_shouldIncrementCounterHistogram",
                                "command_shouldIncrementCounterHistogram"}))
                .describedAs("counter of all executions in the histogram")
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementTotalsForSuccess() {
        // given
        TestHystrixCommand command = new TestHystrixCommand("shouldIncrementCounterHistogram");

        // when
        command.execute();

        // then
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_total",
                        new String[]{"command_group", "command_name"},
                        new String[]{"group_shouldIncrementCounterHistogram",
                                "command_shouldIncrementCounterHistogram"}))
                .describedAs("counter of all executions in the histogram")
                .isEqualTo(1);

        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_error_total",
                        new String[]{"command_group", "command_name"},
                        new String[]{"group_shouldIncrementCounterHistogram",
                                "command_shouldIncrementCounterHistogram"}))
                .describedAs("counter of all executions in the histogram")
                .isEqualTo(0);

    }

    @Test
    public void shouldIncrementTotalsForFailure() {
        // given
        TestHystrixCommand command = new TestHystrixCommand("shouldIncrementCounterHistogram", true);

        // when
        assertThatThrownBy(() ->
                command.execute()).isExactlyInstanceOf(HystrixRuntimeException.class);

        // then
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_total",
                        new String[]{"command_group", "command_name"},
                        new String[]{"group_shouldIncrementCounterHistogram",
                                "command_shouldIncrementCounterHistogram"}))
                .describedAs("counter of all executions in the histogram")
                .isEqualTo(1);

        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_error_total",
                        new String[]{"command_group", "command_name"},
                        new String[]{"group_shouldIncrementCounterHistogram",
                                "command_shouldIncrementCounterHistogram"}))
                .describedAs("counter of all executions in the histogram")
                .isEqualTo(1);

    }

    @Test
    public void shouldWorkWithTwoCommands() {
        // given
        TestHystrixCommand command1 = new TestHystrixCommand("cmd1");
        TestHystrixCommand command2 = new TestHystrixCommand("cmd2");

        // when
        command1.execute();
        command2.execute();

        // then
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_event_total",
                        new String[]{"command_group", "command_name", "event"},
                        new String[]{"group_cmd1", "command_cmd1", "success"}))
                .describedAs("counter of successful executions")
                .isEqualTo(1);
        assertThat(CollectorRegistry.defaultRegistry
                .getSampleValue("exampleapp_hystrix_command_event_total",
                        new String[]{"command_group", "command_name", "event"},
                        new String[]{"group_cmd2", "command_cmd2", "success"}))
                .describedAs("counter of successful executions")
                .isEqualTo(1);
    }

}
