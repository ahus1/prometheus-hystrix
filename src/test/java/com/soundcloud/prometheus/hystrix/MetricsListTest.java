package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.Hystrix;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
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
        HystrixPrometheusMetricsPublisher.register(null, CollectorRegistry.defaultRegistry, false, false);
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
        writer.close();
    }

}
