/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * Implementation of a {@link HystrixMetricsPublisherCollapser} for Prometheus Metrics. See <a
 * href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisherCollapser implements HystrixMetricsPublisherCollapser {

    private Map<String, String> labels = new TreeMap<>();
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
        this.labels.put(HystrixMetricsConstants.COLLAPSER_NAME, key.name());
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        delegate.initialize();

        createCountersForCumulativeEvents();
        createCountersForRollingEvents();
        createCountersForBatchSizeEvents("Collapser the batch size metric.");
        createCountersForShardSizeEvents("Collapser shard size metric.");
        if (exportProperties) {
            createCountersForPropertyEvents("Configuration property partitioned by collapser_name.");
        }
    }

    protected void createCountersForPropertyEvents(String doc) {
        addGauge("property_value_max_requests_in_batch", doc, () -> properties.maxRequestsInBatch().get());

        addGauge("property_value_request_cache_enabled", doc, () -> properties.requestCacheEnabled().get() ? 1 : 0);

        addGauge("property_value_timer_delay_in_milliseconds", doc, () -> properties.timerDelayInMilliseconds().get());

        addGauge("property_value_rolling_statistical_window_in_milliseconds", doc,
            () -> properties.metricsRollingStatisticalWindowInMilliseconds().get());
    }

    public void createCountersForShardSizeEvents(String shardDoc) {
        addGauge("shard_size_mean", shardDoc, metrics::getShardSizeMean);

        createShardSizePercentile("shard_size_percentile_25", 25, shardDoc);
        createShardSizePercentile("shard_size_percentile_50", 50, shardDoc);
        createShardSizePercentile("shard_size_percentile_75", 75, shardDoc);
        createShardSizePercentile("shard_size_percentile_90", 90, shardDoc);
        createShardSizePercentile("shard_size_percentile_99", 99, shardDoc);
        createShardSizePercentile("shard_size_percentile_995", 99.5, shardDoc);
    }

    public void createCountersForBatchSizeEvents(String batchDoc) {
        addGauge("batch_size_mean", batchDoc, metrics::getBatchSizeMean);

        createBatchSizePercentile("batch_size_percentile_25", 25, batchDoc);
        createBatchSizePercentile("batch_size_percentile_50", 50, batchDoc);
        createBatchSizePercentile("batch_size_percentile_75", 75, batchDoc);
        createBatchSizePercentile("batch_size_percentile_90", 90, batchDoc);
        createBatchSizePercentile("batch_size_percentile_99", 99, batchDoc);
        createBatchSizePercentile("batch_size_percentile_995", 99.5, batchDoc);
    }

    public void createCountersForRollingEvents() {
        createRollingCountForEvent("rolling_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createRollingCountForEvent("rolling_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    public void createCountersForCumulativeEvents() {
        createCumulativeCountForEvent("count_batches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createCumulativeCountForEvent("count_requests_batched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    protected void createCumulativeCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are cumulative counts since the start of the application.";
        addGauge(metric, doc, () -> metrics.getCumulativeCount(event));
    }

    protected void createRollingCountForEvent(String metric, final HystrixRollingNumberEvent event) {
        String doc = "These are \"point in time\" counts representing the last X seconds.";
        addGauge(metric, doc, () -> metrics.getRollingCount(event));
    }

    protected void createBatchSizePercentile(String metric, final double percentile, String documentation) {
        addGauge(metric, documentation, () -> metrics.getBatchSizePercentile(percentile));
    }

    protected void createShardSizePercentile(String metric, final double percentile, String documentation) {
        addGauge(metric, documentation, () -> metrics.getShardSizePercentile(percentile));
    }

    protected void addGauge(String metric, String helpDoc, Callable<Number> value) {
        collector.addGauge(HystrixMetricsConstants.HYSTRIX_COLLAPSER, metric, helpDoc, labels, value);
    }
}
