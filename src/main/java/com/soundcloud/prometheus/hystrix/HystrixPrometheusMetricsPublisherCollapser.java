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
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * Implementation of a {@link HystrixMetricsPublisherCollapser} for Prometheus Metrics.
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisherCollapser implements HystrixMetricsPublisherCollapser {

    private final SortedMap<String, String> labels;
    private HystrixMetricsPublisherCollapser delegate;
    private final boolean exportProperties;

    private final HystrixCollapserMetrics metrics;
    private final HystrixCollapserProperties properties;
    private final HystrixMetricsCollector collector;

    public HystrixPrometheusMetricsPublisherCollapser(
            HystrixMetricsCollector collector, HystrixCollapserKey key, HystrixCollapserMetrics metrics,
            HystrixCollapserProperties properties, boolean exportProperties,
            HystrixMetricsPublisherCollapser delegate) {

        this.metrics = metrics;
        this.collector = collector;
        this.properties = properties;
        this.exportProperties = exportProperties;
        this.labels = new TreeMap<String, String>();
        labels.put("collapser_name", key.name());
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        delegate.initialize();

        createCumulativeCountForEvent("count_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createCumulativeCountForEvent("count_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        createRollingCountForEvent("rolling_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createRollingCountForEvent("rolling_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        String batchDoc = "Collapser the batch size metric.";
        addGauge("batch_size_mean", batchDoc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return metrics.getBatchSizeMean();
            }
        });

        createBatchSizePercentile("batch_size_percentile_25", 25, batchDoc);
        createBatchSizePercentile("batch_size_percentile_50", 50, batchDoc);
        createBatchSizePercentile("batch_size_percentile_75", 75, batchDoc);
        createBatchSizePercentile("batch_size_percentile_90", 90, batchDoc);
        createBatchSizePercentile("batch_size_percentile_99", 99, batchDoc);
        createBatchSizePercentile("batch_size_percentile_995", 99.5, batchDoc);

        String shardDoc = "Collapser shard size metric.";
        addGauge("shard_size_mean", shardDoc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
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
            String doc = "Configuration property partitioned by collapser_name.";

            addGauge("property_value_max_requests_in_batch", doc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return properties.maxRequestsInBatch().get();
                        }
                    });

            addGauge("property_value_request_cache_enabled", doc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return properties.requestCacheEnabled().get() ? 1 : 0;
                        }
                    });

            addGauge("property_value_timer_delay_in_milliseconds", doc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return properties.timerDelayInMilliseconds().get();
                        }
                    });

            addGauge("property_value_rolling_statistical_window_in_milliseconds", doc,
                    new Callable<Number>() {
                        @Override
                        public Number call() throws Exception {
                            return properties.metricsRollingStatisticalWindowInMilliseconds().get();
                        }
                    });
        }
    }

    private void createCumulativeCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are cumulative counts since the start of the application.";
        addGauge(metric, doc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return metrics.getCumulativeCount(event);
            }
        });
    }

    private void createRollingCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are \"point in time\" counts representing the last X seconds.";
        addGauge(metric, doc, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return metrics.getRollingCount(event);
            }
        });
    }

    private void createBatchSizePercentile(String metric, final double percentile, String documentation) {
        addGauge(metric, documentation, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return metrics.getBatchSizePercentile(percentile);
            }
        });
    }

    private void createShardSizePercentile(String metric, final double percentile, String documentation) {
        addGauge(metric, documentation, new Callable<Number>() {
            @Override
            public Number call() throws Exception {
                return metrics.getShardSizePercentile(percentile);
            }
        });
    }

    private void addGauge(String metric, String helpDoc, Callable<Number> value) {
        collector.addGauge("hystrix_collapser", metric, helpDoc, labels, value);
    }
}
