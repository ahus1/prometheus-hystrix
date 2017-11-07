package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.Hystrix;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * @author Alexander Schwartz 2017
 */
public class MetricsListTest {

    @After
    public void teardown() {
        Hystrix.reset();
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    public void shouldWriteNiceMetricsOutput() throws IOException {
        // given
        HystrixPrometheusMetricsPublisher.builder().shouldExportDeprecatedMetrics(false).buildAndRegister();
        TestHystrixCommand command = new TestHystrixCommand("any");

        // when
        command.execute();

        // then
        Writer writer = new FileWriter("target/sample.txt");
        try {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            writer.flush();
        } finally {
            writer.close();
        }
    }

    @Test
    public void shouldHaveExponentialBuckets() throws IOException {
        // given
        HystrixPrometheusMetricsPublisher.builder().withExponentialBuckets().buildAndRegister();
        TestHystrixCommand command = new TestHystrixCommand("any");

        // when
        command.execute();

        // then
        StringWriter writer = new StringWriter();
        try {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            writer.flush();
        } finally {
            writer.close();
        }
        String result = writer.toString();
        Assertions.assertThat(result).contains("le=\"0.001\"");
        Assertions.assertThat(result).contains("le=\"2.5169093494697568\"");
    }

    @Test
    public void shouldHaveLinearBuckets() throws IOException {
        // given
        HystrixPrometheusMetricsPublisher.builder().withLinearBuckets(0.1, 0.2, 3).buildAndRegister();
        TestHystrixCommand command = new TestHystrixCommand("any");

        // when
        command.execute();

        // then
        StringWriter writer = new StringWriter();
        try {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            writer.flush();
        } finally {
            writer.close();
        }
        String result = writer.toString();
        Assertions.assertThat(result).contains("le=\"0.1\"");
        Assertions.assertThat(result).contains("le=\"0.5\"");
    }

    @Test
    public void shouldHaveDistinctBuckets() throws IOException {
        // given
        HystrixPrometheusMetricsPublisher.builder().withBuckets(0.1, 1.0).buildAndRegister();
        TestHystrixCommand command = new TestHystrixCommand("any");

        // when
        command.execute();

        // then
        StringWriter writer = new StringWriter();
        try {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            writer.flush();
        } finally {
            writer.close();
        }
        String result = writer.toString();
        Assertions.assertThat(result).contains("le=\"0.1\"");
        Assertions.assertThat(result).contains("le=\"1.0\"");
    }

}
