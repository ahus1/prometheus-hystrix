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

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * <p>Implementation of {@link HystrixMetricsPublisherCommand} using the <a href="https://github.com/prometheus/client_java">Prometheus Java Client</a>.</p>
 * <p>This class is based on <a href="https://github.com/Netflix/Hystrix/blob/master/hystrix-contrib/hystrix-codahale-metrics-publisher/src/main/java/com/netflix/hystrix/contrib/codahalemetricspublisher/HystrixCodaHaleMetricsPublisherCommand.java">HystrixCodaHaleMetricsPublisherCommand</a>.</p>
 * <p>For a description of the hystrix metrics see the <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-metrics">Hystrix Metrics &amp; Monitoring wiki</a>.<p/>
 */
public class HystrixPrometheusMetricsPublisherCommand implements HystrixMetricsPublisherCommand {

    private static final String SUBSYSTEM = "hystrix_command";

    private final Map<String, String> labels;
    private final boolean exportProperties;

    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final PrometheusMetricsCollector collector;

    public HystrixPrometheusMetricsPublisherCommand(
            PrometheusMetricsCollector collector, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties, boolean exportProperties) {

        this.labels = new HashMap<>();
        this.labels.put("command_group", (commandGroupKey != null) ? commandGroupKey.name() : "default");
        this.labels.put("command_name", commandKey.name());

        this.exportProperties = exportProperties;

        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.collector = collector;
        this.metrics = metrics;
    }

    @Override
    public void initialize() {
        String circuitDoc = "Current status of circuit breaker: 1 = open, 0 = closed.";
        collector.addGauge(SUBSYSTEM, "is_circuit_breaker_open", circuitDoc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return booleanToNumber(circuitBreaker.isOpen());
            }
        });
        String permitsDoc = "The number of executionSemaphorePermits in use right now.";
        collector.addGauge(SUBSYSTEM, "execution_semaphore_permits_in_use", permitsDoc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getCurrentConcurrentExecutionCount();
            }
        });
        String errorsDoc = "Error percentage derived from current metrics.";
        collector.addGauge(SUBSYSTEM, "error_percentage", errorsDoc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getHealthCounts().getErrorPercentage();
            }
        });

        createCumulativeCountForEvent("count_bad_requests", HystrixRollingNumberEvent.BAD_REQUEST);
        createCumulativeCountForEvent("count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);
        createCumulativeCountForEvent("count_emit", HystrixRollingNumberEvent.EMIT);
        createCumulativeCountForEvent("count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createCumulativeCountForEvent("count_failure", HystrixRollingNumberEvent.FAILURE);
        createCumulativeCountForEvent("count_fallback_emit", HystrixRollingNumberEvent.FALLBACK_EMIT);
        createCumulativeCountForEvent("count_fallback_failure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createCumulativeCountForEvent("count_fallback_rejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createCumulativeCountForEvent("count_fallback_success", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createCumulativeCountForEvent("count_responses_from_cache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
        createCumulativeCountForEvent("count_semaphore_rejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
        createCumulativeCountForEvent("count_short_circuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCumulativeCountForEvent("count_success", HystrixRollingNumberEvent.SUCCESS);
        createCumulativeCountForEvent("count_thread_pool_rejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createCumulativeCountForEvent("count_timeout", HystrixRollingNumberEvent.TIMEOUT);

        createRollingCountForEvent("rolling_count_bad_requests", HystrixRollingNumberEvent.BAD_REQUEST);
        createRollingCountForEvent("rolling_count_collapsed_requests", HystrixRollingNumberEvent.COLLAPSED);
        createRollingCountForEvent("rolling_count_emit", HystrixRollingNumberEvent.EMIT);
        createRollingCountForEvent("rolling_count_exceptions_thrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createRollingCountForEvent("rolling_count_failure", HystrixRollingNumberEvent.FAILURE);
        createRollingCountForEvent("rolling_count_fallback_emit", HystrixRollingNumberEvent.FALLBACK_EMIT);
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

        collector.addGauge(SUBSYSTEM, "latency_execute_mean", latencyExecuteDescription, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getExecutionTimeMean();
            }
        });
        createExcecutionTimePercentile("latency_execute_percentile_5", 5, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_25", 25, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_50", 50, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_75", 75, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_90", 90, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_99", 99, latencyExecuteDescription);
        createExcecutionTimePercentile("latency_execute_percentile_995", 99.5, latencyExecuteDescription);

        final String latencyTotalDescription = "Rolling percentiles of execution times for the "
                + "end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until "
                + "a response is returned (or ready to return in case of queue(). The purpose of this "
                + "compared with the latency_execute* percentiles is to measure the cost of thread "
                + "queuing/scheduling/execution, semaphores, circuit breaker logic and other "
                + "aspects of overhead (including metrics capture itself).";

        collector.addGauge(SUBSYSTEM, "latency_total_mean", latencyTotalDescription, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getTotalTimeMean();
            }
        });
        createTotalTimePercentile("latency_total_percentile_5", 5, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_25", 25, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_50", 50, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_75", 75, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_90", 90, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_99", 99, latencyTotalDescription);
        createTotalTimePercentile("latency_total_percentile_995", 99.5, latencyTotalDescription);

        if (exportProperties) {
            final String propDesc = "These informational metrics report the "
                    + "actual property values being used by the HystrixCommand. This is useful to "
                    + "see when a dynamic property takes effect and confirm a property is set as "
                    + "expected.";

            createIntegerProperty("property_value_rolling_statistical_window_in_milliseconds",
                    properties.metricsRollingStatisticalWindowInMilliseconds(), propDesc);
            createIntegerProperty("property_value_circuit_breaker_request_volume_threshold",
                    properties.circuitBreakerRequestVolumeThreshold(), propDesc);
            createIntegerProperty("property_value_circuit_breaker_sleep_window_in_milliseconds",
                    properties.circuitBreakerSleepWindowInMilliseconds(), propDesc);
            createIntegerProperty("property_value_circuit_breaker_error_threshold_percentage",
                    properties.circuitBreakerErrorThresholdPercentage(), propDesc);
            createBooleanProperty("property_value_circuit_breaker_force_open",
                    properties.circuitBreakerForceOpen(), propDesc);
            createBooleanProperty("property_value_circuit_breaker_force_closed",
                    properties.circuitBreakerForceClosed(), propDesc);
            createIntegerProperty("property_value_execution_timeout_in_milliseconds",
                    properties.executionTimeoutInMilliseconds(), propDesc);
            createEnumProperty("property_value_execution_isolation_strategy",
                    properties.executionIsolationStrategy(), propDesc);
            createBooleanProperty("property_value_metrics_rolling_percentile_enabled",
                    properties.metricsRollingPercentileEnabled(), propDesc);
            createBooleanProperty("property_value_request_cache_enabled",
                    properties.requestCacheEnabled(), propDesc);
            createBooleanProperty("property_value_request_log_enabled",
                    properties.requestLogEnabled(), propDesc);
            createIntegerProperty("property_value_execution_isolation_semaphore_max_concurrent_requests",
                    properties.executionIsolationSemaphoreMaxConcurrentRequests(), propDesc);
            createIntegerProperty("property_value_fallback_isolation_semaphore_max_concurrent_requests",
                    properties.fallbackIsolationSemaphoreMaxConcurrentRequests(), propDesc);
        }
    }

    private void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        String doc = "These are cumulative counts since the start of the application.";
        collector.addGauge(SUBSYSTEM, name, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getCumulativeCount(event);
            }
        });
    }

    private void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        String doc = "These are \"point in time\" counts representing the last X seconds.";
        collector.addGauge(SUBSYSTEM, name, doc, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getRollingCount(event);
            }
        });
    }

    private void createExcecutionTimePercentile(String name, final double percentile, String documentation) {
        collector.addGauge(SUBSYSTEM, name, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getExecutionTimePercentile(percentile);
            }
        });
    }

    private void createTotalTimePercentile(String name, final double percentile, String documentation) {
        collector.addGauge(SUBSYSTEM, name, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getTotalTimePercentile(percentile);
            }
        });
    }

    private void createIntegerProperty(String name, final HystrixProperty<Integer> property, String documentation) {
        collector.addGauge(SUBSYSTEM, name, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return property.get();
            }
        });
    }

    private void createBooleanProperty(String name, final HystrixProperty<Boolean> property, String documentation) {
        collector.addGauge(SUBSYSTEM, name, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return booleanToNumber(property.get());
            }
        });
    }

    private void createEnumProperty(String name, final HystrixProperty<? extends Enum> property, String documentation) {
        collector.addGauge(SUBSYSTEM, name, documentation, labels, new Callable<Number>() {
            @Override
            public Number call() {
                return property.get().ordinal();
            }
        });
    }

    private int booleanToNumber(boolean value) {
        return value ? 1 : 0;
    }
}
