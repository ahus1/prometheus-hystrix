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
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * <p>Implementation of {@link HystrixMetricsPublisherThreadPool} using the <a href="https://github.com/prometheus/client_java">Prometheus Java Client</a>.</p>
 * <p>This class is based on the <a href="https://github.com/Netflix/Hystrix/blob/master/hystrix-contrib/hystrix-codahale-metrics-publisher/src/main/java/com/netflix/hystrix/contrib/codahalemetricspublisher/HystrixCodaHaleMetricsPublisherThreadPool.java">HystrixCodaHaleMetricsPublisherThreadPool</a>.</p>
 * <p>For a description of the hystrix metrics see the <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#threadpool-metrics">Hystrix Metrics &amp; Monitoring wiki</a>.<p/>
 */
public class HystrixPrometheusMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool, Runnable {

    private final Map<String, Callable<Number>> values = new HashMap<String, Callable<Number>>();

    private final String poolName;
    private final boolean exportProperties;

    private final GaugeRegistry registry;
    private final HystrixThreadPoolMetrics metrics;
    private final HystrixThreadPoolProperties properties;

    public HystrixPrometheusMetricsPublisherThreadPool(
            GaugeRegistry registry, HystrixThreadPoolKey key, HystrixThreadPoolMetrics metrics,
            HystrixThreadPoolProperties properties, boolean exportProperties) {

        this.poolName = key.name();
        this.exportProperties = exportProperties;

        this.metrics = metrics;
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public void initialize() {
        final String currentStateDoc = "Current state of thread-pool partitioned by pool_name.";

        values.put(createMetricName("thread_active_count", currentStateDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentActiveCount();
                    }
                }
        );
        values.put(createMetricName("completed_task_count", currentStateDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentCompletedTaskCount();
                    }
                }
        );
        values.put(createMetricName("largest_pool_size", currentStateDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentLargestPoolSize();
                    }
                }
        );
        values.put(createMetricName("total_task_count", currentStateDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentTaskCount();
                    }
                }
        );
        values.put(createMetricName("queue_size", currentStateDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentQueueSize();
                    }
                }
        );

        final String rollingCountDoc = "Rolling count partitioned by pool_name.";

        values.put(createMetricName("rolling_max_active_threads", rollingCountDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getRollingMaxActiveThreads();
                    }
                }
        );

        values.put(createMetricName("rolling_count_commands_rejected", rollingCountDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
                    }
                }
        );

        values.put(createMetricName("rolling_count_threads_executed", rollingCountDoc),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getRollingCountThreadsExecuted();
                    }
                }
        );

        values.put(createMetricName("count_threads_executed", "Cumulative count partitioned by pool_name."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCumulativeCountThreadsExecuted();
                    }
                }
        );

        if (exportProperties) {
            createIntegerProperty("property_value_core_pool_size", properties.coreSize());
            createIntegerProperty("property_value_keep_alive_time_in_minutes", properties.keepAliveTimeMinutes());
            createIntegerProperty("property_value_queue_size_rejection_threshold", properties.queueSizeRejectionThreshold());
            createIntegerProperty("property_value_max_queue_size", properties.maxQueueSize());
        }
    }

    /**
     * Export current hystrix metrix into Prometheus.
     */
    @Override
    public void run() {
        registry.recordMetrics(values, poolName);
    }

    private String createMetricName(String metric, String documentation) {
        return registry.registerGauge("hystrix_thread_pool", metric, documentation, "pool_name");
    }

    private void createIntegerProperty(String name, final HystrixProperty<Integer> property) {
        values.put(createMetricName(name, "Configuration property partitioned by pool_name."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return property.get();
                    }
                }
        );
    }
}
