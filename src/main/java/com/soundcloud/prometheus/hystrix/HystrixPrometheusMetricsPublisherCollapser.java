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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Implementation of {@link HystrixMetricsPublisherCollapser} using Prometheus Metrics.
 */
public class HystrixPrometheusMetricsPublisherCollapser implements HystrixMetricsPublisherCollapser, Runnable {

    private final Map<String, Callable<Number>> values = new HashMap<String, Callable<Number>>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String collapserName;
    private final boolean exportProperties;

    private final GaugeRegistry registry;
    private final HystrixCollapserMetrics metrics;
    private final HystrixCollapserProperties properties;

    public HystrixPrometheusMetricsPublisherCollapser(
            GaugeRegistry registry, HystrixCollapserKey key, HystrixCollapserMetrics metrics,
            HystrixCollapserProperties properties, boolean exportProperties) {

        this.metrics = metrics;
        this.registry = registry;
        this.properties = properties;
        this.collapserName = key.name();
        this.exportProperties = exportProperties;
    }

    @Override
    public void initialize() {
        createCumulativeCountForEvent("count_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createCumulativeCountForEvent("count_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        createRollingCountForEvent("rolling_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createRollingCountForEvent("rolling_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        final String batchDoc = "Collapser the batch size metric.";

        values.put(createMetricName("batch_size_mean", batchDoc), new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getBatchSizeMean();
            }
        });

        createBatchSizePercentile("batch_size_percentile_25", 25, batchDoc);
        createBatchSizePercentile("batch_size_percentile_50", 50, batchDoc);
        createBatchSizePercentile("batch_size_percentile_75", 75, batchDoc);
        createBatchSizePercentile("batch_size_percentile_90", 90, batchDoc);
        createBatchSizePercentile("batch_size_percentile_99", 99, batchDoc);
        createBatchSizePercentile("batch_size_percentile_995", 99.5, batchDoc);

        final String shardDoc = "Collapser shard size metric.";

        values.put(createMetricName("shard_size_mean", shardDoc), new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getShardSizeMean();
            }
        });

        createShardSizePercentile("shard_size_percentile_25", 25, shardDoc);
        createShardSizePercentile("shard_size_percentile_50", 50, shardDoc);
        createShardSizePercentile("shard_size_percentile_75", 75, shardDoc);
        createShardSizePercentile("shard_size_percentile_90", 90, shardDoc);
        createShardSizePercentile("shard_size_percentile_99", 99, shardDoc);
        createShardSizePercentile("shard_size_percentile_995", 99.5, shardDoc);

        if (exportProperties) {
            createIntegerProperty("property_value_rolling_statistical_window_in_milliseconds",
                    properties.metricsRollingStatisticalWindowInMilliseconds());
            createBooleanProperty("property_value_request_cache_enabled",
                    properties.requestCacheEnabled());
            createIntegerProperty("property_value_max_requests_in_batch",
                    properties.maxRequestsInBatch());
            createIntegerProperty("property_value_timer_delay_in_milliseconds",
                    properties.timerDelayInMilliseconds());
        }
    }

    /**
     * Export current hystrix metrix into Prometheus.
     */
    @Override
    public void run() {
        for (Map.Entry<String, Callable<Number>> metric : values.entrySet()) {
            try {
                double value = metric.getValue().call().doubleValue();
                registry.getGauge(metric.getKey()).labels(collapserName).set(value);
            } catch (Exception e) {
                logger.warn(String.format("Cannot export %s gauge for %s - caused by: %s",
                        metric.getKey(), collapserName, e));
            }
        }
    }

    private String createMetricName(String metric, String documentation) {
        return registry.registerGauge("hystrix_collapser", metric, documentation, "collapser_name");
    }

    private void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        values.put(createMetricName(name, "These are cumulative counts since the start of the application."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCumulativeCount(event);
                    }
                });
    }

    private void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        values.put(createMetricName(name, "These are \"point in time\" counts representing the last X seconds."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getRollingCount(event);
                    }
                });
    }

    private void createBatchSizePercentile(String name, final double percentile, String documentation) {
        values.put(createMetricName(name, documentation),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getBatchSizePercentile(percentile);
                    }
                });
    }

    private void createShardSizePercentile(String name, final double percentile, String documentation) {
        values.put(createMetricName(name, documentation),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getShardSizePercentile(percentile);
                    }
                });
    }

    private void createIntegerProperty(String name, final HystrixProperty<Integer> property) {
        values.put(createMetricName(name, "Configuration property partitioned by collapser_name."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return property.get();
                    }
                });
    }

    private void createBooleanProperty(String name, final HystrixProperty<Boolean> property) {
        values.put(createMetricName(name, "Configuration property partitioned by collapser_name."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return property.get() ? 1 : 0;
                    }
                });
    }
}
