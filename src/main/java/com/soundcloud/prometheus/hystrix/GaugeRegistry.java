/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soundcloud.prometheus.hystrix;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class GaugeRegistry {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConcurrentHashMap<String, Gauge> gauges = new ConcurrentHashMap<String, Gauge>();

    private final CollectorRegistry registry;
    private final String namespace;

    public GaugeRegistry(CollectorRegistry registry, String namespace) {
        this.namespace = namespace;
        this.registry = registry;
    }

    /**
     * Register a Gauge for the given subsystem and metric, and return the metricName to be used to lookup the Gauge.
     *
     * @see #getGauge(String)
     */
    public String registerGauge(String subsystem, String metric, String documentation, String... labelNames) {
        String metricName = subsystem + ":" + metric;
        Gauge gauge = Gauge.build()
                .namespace(namespace)
                .subsystem(subsystem)
                .name(metric)
                .help(documentation)
                .labelNames(labelNames)
                .create();
        Gauge existing = gauges.putIfAbsent(metricName, gauge);
        if (existing == null) {
            registry.register(gauge);
        }
        return metricName;
    }

    /**
     * Returns a previously-registered Gauge for the given metricName.
     *
     * @see #registerGauge(String, String, String, String...)
     */
    public Gauge getGauge(String metricName) {
        return gauges.get(metricName);
    }

    /**
     * Given a map of metricName to metric mappings, and the label values to apply to each metric,
     * fetch the current value of each metric and record it in it's corresponding gauge.
     *
     * @see #registerGauge(String, String, String, String...)
     */
    public void recordMetrics(Map<String, Callable<Number>> metrics, String... labelValues) {
        for (Map.Entry<String, Callable<Number>> metric : metrics.entrySet()) {
            try {
                double value = metric.getValue().call().doubleValue();
                getGauge(metric.getKey()).labels(labelValues).set(value);
            } catch (Exception e) {
                logger.warn(String.format("Cannot export %s gauge for %s - caused by: %s",
                        metric.getKey(), StringUtils.join(labelValues, " & "), e), e);
            }
        }
    }
}
