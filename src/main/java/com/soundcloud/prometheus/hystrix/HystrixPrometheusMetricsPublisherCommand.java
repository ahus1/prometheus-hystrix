/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import io.prometheus.client.Prometheus;
import io.prometheus.client.Prometheus.ExpositionHook;
import io.prometheus.client.metrics.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Implementation of {@link HystrixMetricsPublisherCommand} using the <a href="https://github.com/prometheus/client_java">Prometheus Java Client</a>.</p>
 *
 * <p>This class is based on <a href="https://github.com/Netflix/Hystrix/blob/master/hystrix-contrib/hystrix-codahale-metrics-publisher/src/main/java/com/netflix/hystrix/contrib/codahalemetricspublisher/HystrixCodaHaleMetricsPublisherCommand.java">HystrixCodaHaleMetricsPublisherCommand</a>.</p>
 *
 * <p>For a description of the hystrix metrics see the <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-metrics">Hystrix Metrics &amp; Monitoring wiki</a>.<p/>
 */
public class HystrixPrometheusMetricsPublisherCommand implements HystrixMetricsPublisherCommand, ExpositionHook {

    private static final String SUBSYSTEM = "hystrix_command";
    private static final String COMMAND_NAME = "command_name";
    private static final String COMMAND_GROUP = "command_group";

    private static final Gauge.Builder gaugeTmpl = Gauge.newBuilder()
            .subsystem(SUBSYSTEM)
            .labelNames(COMMAND_GROUP, COMMAND_NAME)
            .registerStatic(false);

    // Hysterix instantiates N instances of this class, one for each command.  Thusly the inventory
    // of metrics must always remain static, unless the metrics are statically defined as fields
    // in the class, which is the idiomatic approach to their definition.
    private static final ConcurrentHashMap<String, Gauge.Partial> gauges =
            new ConcurrentHashMap<String, Gauge.Partial>();

    private final Logger logger =
            LoggerFactory.getLogger(HystrixPrometheusMetricsPublisherCommand.class);

    private final Map<String, Callable<Number>> values = new HashMap<String, Callable<Number>>();

    private final String namespace;
    private final String commandName;
    private final String commandGroup;
    private final boolean exportProperties;

    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;

    public HystrixPrometheusMetricsPublisherCommand(
            String namespace, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties, boolean exportProperties) {

        this.namespace = namespace;
        this.commandName = commandKey.name();
        this.commandGroup = (commandGroupKey != null) ? commandGroupKey.name() : "default";
        this.exportProperties = exportProperties;

        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void initialize() {
        Prometheus.defaultAddPreexpositionHook(this);

        values.put(createMetricName("is_circuit_breaker_open", "Current status of circuit breaker: 1 = open, 0 = closed."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return booleanToNumber(circuitBreaker.isOpen());
                    }
                }
        );
        values.put(createMetricName("execution_semaphore_permits_in_use", "The number of executionSemaphorePermits in use right now."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCurrentConcurrentExecutionCount();
                    }
                }
        );
        values.put(createMetricName("error_percentage", "Error percentage derived from current metrics."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getHealthCounts().getErrorPercentage();
                    }
                }
        );

        createCumulativeCountForEvent("count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);
        createCumulativeCountForEvent("count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createCumulativeCountForEvent("count_failure", HystrixRollingNumberEvent.FAILURE);
        createCumulativeCountForEvent("count_fallback_failure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createCumulativeCountForEvent("count_fallback_rejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createCumulativeCountForEvent("count_fallback_success", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
        createCumulativeCountForEvent("count_semaphore_rejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
        createCumulativeCountForEvent("count_short_circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCumulativeCountForEvent("count_success", HystrixRollingNumberEvent.SUCCESS);
        createCumulativeCountForEvent("count_thread_pool_rejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createCumulativeCountForEvent("count_timeout", HystrixRollingNumberEvent.TIMEOUT);

        createRollingCountForEvent("rolling_count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);
        createRollingCountForEvent("rolling_count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createRollingCountForEvent("rolling_count_failure", HystrixRollingNumberEvent.FAILURE);
        createRollingCountForEvent("rolling_count_fallback_failure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createRollingCountForEvent("rolling_count_fallback_rejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createRollingCountForEvent("rolling_count_fallback_success", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createRollingCountForEvent("rolling_count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
        createRollingCountForEvent("rolling_count_semaphore_rejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
        createRollingCountForEvent("rolling_count_short_circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createRollingCountForEvent("rolling_count_success", HystrixRollingNumberEvent.SUCCESS);
        createRollingCountForEvent("rolling_count_thread_pool_rejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createRollingCountForEvent("rolling_count_timeout", HystrixRollingNumberEvent.TIMEOUT);

        final String latencyExecuteDescription = "Rolling percentiles of execution times for the "
                + "HystrixCommand.run() method (on the child thread if using thread isolation).";

        values.put(createMetricName("latency_execute_mean", latencyExecuteDescription),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getExecutionTimeMean();
                    }
                }
        );
        values.put(createMetricName("latency_execute_percentile_5", latencyExecuteDescription),
                executionLatencyCallbackFor(5));
        values.put(createMetricName("latency_execute_percentile_25", latencyExecuteDescription),
                executionLatencyCallbackFor(25));
        values.put(createMetricName("latency_execute_percentile_50", latencyExecuteDescription),
                executionLatencyCallbackFor(50));
        values.put(createMetricName("latency_execute_percentile_75", latencyExecuteDescription),
                executionLatencyCallbackFor(75));
        values.put(createMetricName("latency_execute_percentile_90", latencyExecuteDescription),
                executionLatencyCallbackFor(90));
        values.put(createMetricName("latency_execute_percentile_99", latencyExecuteDescription),
                executionLatencyCallbackFor(99));
        values.put(createMetricName("latency_execute_percentile_995", latencyExecuteDescription),
                executionLatencyCallbackFor(99.5f));

        final String latencyTotalDescription = "Rolling percentiles of execution times for the "
                + "end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until "
                + "a response is returned (or ready to return in case of queue(). The purpose of this "
                + "compared with the latency_execute* percentiles is to measure the cost of thread "
                + "queuing/scheduling/execution, semaphores, circuit breaker logic and other "
                + "aspects of overhead (including metrics capture itself).";

        values.put(createMetricName("latency_total_mean", latencyTotalDescription),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getTotalTimeMean();
                    }
                }
        );
        values.put(createMetricName("latency_total_percentile_5", latencyTotalDescription),
                executionTotalLatencyCallbackFor(5));
        values.put(createMetricName("latency_total_percentile_25", latencyTotalDescription),
                executionTotalLatencyCallbackFor(25));
        values.put(createMetricName("latency_total_percentile_50", latencyTotalDescription),
                executionTotalLatencyCallbackFor(50));
        values.put(createMetricName("latency_total_percentile_75", latencyTotalDescription),
                executionTotalLatencyCallbackFor(75));
        values.put(createMetricName("latency_total_percentile_90", latencyTotalDescription),
                executionTotalLatencyCallbackFor(90));
        values.put(createMetricName("latency_total_percentile_99", latencyTotalDescription),
                executionTotalLatencyCallbackFor(99));
        values.put(createMetricName("latency_total_percentile_995", latencyTotalDescription),
                executionTotalLatencyCallbackFor(99.5f));

        if (exportProperties) {
            final String propDesc = "These informational metrics report the "
                    + "actual property values being used by the HystrixCommand. This is useful to "
                    + "see when a dynamic property takes effect and confirm a property is set as "
                    + "expected.";

            values.put(createMetricName("property_value_rolling_statistical_window_in_milliseconds", propDesc),
                    numericPropertyCallbackFor(properties.metricsRollingStatisticalWindowInMilliseconds())
            );
            values.put(createMetricName("property_value_circuit_breaker_request_volume_threshold", propDesc),
                    numericPropertyCallbackFor(properties.circuitBreakerRequestVolumeThreshold()));
            values.put(createMetricName("property_value_circuit_breaker_sleep_window_in_milliseconds", propDesc),
                    numericPropertyCallbackFor(properties.circuitBreakerSleepWindowInMilliseconds()));
            values.put(createMetricName("property_value_circuit_breaker_error_threshold_percentage", propDesc),
                    numericPropertyCallbackFor(properties.circuitBreakerErrorThresholdPercentage()));
            values.put(createMetricName("property_value_circuit_breaker_force_open", propDesc),
                    booleanPropertyCallbackFor(properties.circuitBreakerForceOpen()));
            values.put(createMetricName("property_value_circuit_breaker_force_closed", propDesc),
                    booleanPropertyCallbackFor(properties.circuitBreakerForceClosed()));
            values.put(createMetricName("property_value_execution_isolation_thread_timeout_in_milliseconds", propDesc),
                    numericPropertyCallbackFor(properties.executionIsolationThreadTimeoutInMilliseconds()));
            values.put(createMetricName("property_value_execution_isolation_strategy", propDesc),
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.executionIsolationStrategy().get().ordinal();
                        }
                    }
            );
            values.put(createMetricName("property_value_metrics_rolling_percentile_enabled", propDesc),
                    booleanPropertyCallbackFor(properties.metricsRollingPercentileEnabled()));
            values.put(createMetricName("property_value_request_cache_enabled", propDesc),
                    booleanPropertyCallbackFor(properties.requestCacheEnabled()));
            values.put(createMetricName("property_value_request_log_enabled", propDesc),
                    booleanPropertyCallbackFor(properties.requestLogEnabled()));
            values.put(createMetricName("property_value_execution_isolation_semaphore_max_concurrent_requests", propDesc),
                    numericPropertyCallbackFor(properties.executionIsolationSemaphoreMaxConcurrentRequests()));
            values.put(createMetricName("property_value_fallback_isolation_semaphore_max_concurrent_requests", propDesc),
                    numericPropertyCallbackFor(properties.fallbackIsolationSemaphoreMaxConcurrentRequests()));
        }
    }

    private Callable<Number> numericPropertyCallbackFor(final HystrixProperty<Integer> prop) {
        return new Callable<Number>() {
            @Override
            public Number call() {
                return prop.get();
            }
        };
    }

    private Callable<Number> booleanPropertyCallbackFor(final HystrixProperty<Boolean> prop) {
        return new Callable<Number>() {
            @Override
            public Number call() {
                return booleanToNumber(prop.get());
            }
        };
    }


    private Callable<Number> executionLatencyCallbackFor(final float rank) {
        return new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getExecutionTimePercentile(rank);
            }
        };
    }

    private Callable<Number> executionTotalLatencyCallbackFor(final float rank) {
        return new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getTotalTimePercentile(rank);
            }
        };
    }

    @Override
    public void run() {
        for (Entry<String, Callable<Number>> metric : values.entrySet()) {
            try {
                double value = metric.getValue().call().doubleValue();
                gauges.get(metric.getKey())
                        .labelPair(COMMAND_GROUP, commandGroup)
                        .labelPair(COMMAND_NAME, commandName)
                        .apply()
                        .set(value);
            } catch (Exception e) {
                logger.warn(String.format("Cannot export %s gauge for %s %s",
                        metric.getKey(), commandGroup, commandName), e);
            }
        }
    }

    private void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        values.put(createMetricName(name, "These are cumulative counts since the start of the application."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getCumulativeCount(event);
                    }
                }
        );
    }

    private void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        values.put(createMetricName(name, "These are \"point in time\" counts representing the last X seconds."),
                new Callable<Number>() {
                    @Override
                    public Number call() {
                        return metrics.getRollingCount(event);
                    }
                }
        );
    }

    private int booleanToNumber(boolean value) {
        return value ? 1 : 0;
    }

    private String createMetricName(String metric, String documentation) {
        String metricName = String.format("%s,%s,%s", namespace, SUBSYSTEM, metric);
        registerGauge(metricName, namespace, metric, documentation);
        return metricName;
    }

    /**
     * An instance of this class is created for each Hystrix command but our gauges are configured for
     * each metric within a given namespace. Although the {@link #initialize()} method is only called once
     * for each command by {@link com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory} in a
     * thread-safe manner, this method will still be called more than once for each metric across multiple
     * threads so we should ensure that the gauge is only registered once.
     */
    private static void registerGauge(String metricName, String namespace, String metric, String documentation) {
        // Metrics can be built from immutable templates.
        Gauge gauge = gaugeTmpl
                .namespace(namespace)
                .name(metric)
                .documentation(documentation)
                .build();
        // Metrics partials can be prepopulated with label value pairs and then be #apply-ed on
        // demand for mutation.
        Gauge.Partial partial = gauge.newPartial();

        Gauge.Partial existing = gauges.putIfAbsent(metricName, partial);
        if (existing == null) {
            Prometheus.defaultRegister(gauge);
        }
    }
}
