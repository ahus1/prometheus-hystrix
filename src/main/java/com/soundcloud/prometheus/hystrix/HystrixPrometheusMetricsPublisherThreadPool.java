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

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * <p>Implementation of {@link HystrixMetricsPublisherThreadPool} using the <a href="https://github.com/prometheus/client_java">Prometheus Java Client</a>.</p>
 * <p>This class is based on the <a href="https://github.com/Netflix/Hystrix/blob/master/hystrix-contrib/hystrix-codahale-metrics-publisher/src/main/java/com/netflix/hystrix/contrib/codahalemetricspublisher/HystrixCodaHaleMetricsPublisherThreadPool.java">HystrixCodaHaleMetricsPublisherThreadPool</a>.</p>
 * <p>For a description of the hystrix metrics see the <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#threadpool-metrics">Hystrix Metrics &amp; Monitoring wiki</a>.<p/>
 */
public class HystrixPrometheusMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool {

    private static final String SUBSYSTEM = "hystrix_thread_pool";

    private final Map<String, String> labels;
    private final boolean exportProperties;

    private final HystrixThreadPoolMetrics metrics;
    private final HystrixThreadPoolProperties properties;
    private final PrometheusMetricsCollector collector;

    public HystrixPrometheusMetricsPublisherThreadPool(
            PrometheusMetricsCollector collector, HystrixThreadPoolKey key, HystrixThreadPoolMetrics metrics,
            HystrixThreadPoolProperties properties, boolean exportProperties) {

        this.labels = Collections.singletonMap("pool_name", key.name());
        this.exportProperties = exportProperties;

        this.metrics = metrics;
        this.collector = collector;
        this.properties = properties;
    }

    @Override
    public void initialize() {
        String currentStateDoc = "Current state of thread-pool partitioned by pool_name.";
        register("thread_active_count", currentStateDoc, metrics::getCurrentActiveCount);
        register("completed_task_count", currentStateDoc, metrics::getCurrentCompletedTaskCount);
        register("largest_pool_size", currentStateDoc, metrics::getCurrentLargestPoolSize);
        register("total_task_count", currentStateDoc, metrics::getCurrentTaskCount);
        register("queue_size", currentStateDoc, metrics::getCurrentQueueSize);

        String rollDoc = "Rolling count partitioned by pool_name.";
        register("rolling_max_active_threads", rollDoc, metrics::getRollingMaxActiveThreads);
        register("rolling_count_threads_executed", rollDoc, metrics::getRollingCountThreadsExecuted);
        register("rolling_count_commands_rejected", rollDoc, () -> metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));

        String totalDoc = "Cumulative count partitioned by pool_name.";
        register("count_threads_executed", totalDoc, metrics::getCumulativeCountThreadsExecuted);

        if (exportProperties) {
            String doc = "Configuration property partitioned by pool_name.";
            register("property_value_core_pool_size", doc, () -> properties.coreSize().get());
            register("property_value_keep_alive_time_in_minutes", doc, () -> properties.keepAliveTimeMinutes().get());
            register("property_value_queue_size_rejection_threshold", doc, () -> properties.queueSizeRejectionThreshold().get());
            register("property_value_max_queue_size", doc, () -> properties.maxQueueSize().get());
        }
    }

    private void register(String metric, String helpDoc, Callable<Number> value) {
        collector.addGauge(SUBSYSTEM, metric, helpDoc, labels, value);
    }
}
