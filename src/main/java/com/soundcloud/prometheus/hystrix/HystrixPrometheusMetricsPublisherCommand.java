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

import com.netflix.hystrix.*;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action1;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Implementation of a {@link HystrixMetricsPublisherCommand} for Prometheus Metrics.
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisherCommand implements HystrixMetricsPublisherCommand {

    private final static Logger LOG = LoggerFactory.getLogger(HystrixPrometheusMetricsPublisherCommand.class);

    private final SortedMap<String, String> labels;
    private final boolean exportProperties;

    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final HystrixMetricsCollector collector;
    private final HystrixCommandKey commandKey;
    private HystrixMetricsPublisherCommand delegate;
    private boolean exportDeprecatedMetrics;

    public HystrixPrometheusMetricsPublisherCommand(
            HystrixMetricsCollector collector, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties, boolean exportProperties, boolean exportDeprecatedMetrics,
            HystrixMetricsPublisherCommand delegate) {

        this.labels = new TreeMap<String, String>();
        this.labels.put("command_group", (commandGroupKey != null) ? commandGroupKey.name() : "default");
        this.labels.put("command_name", commandKey.name());

        this.exportProperties = exportProperties;

        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.collector = collector;
        this.metrics = metrics;
        this.commandKey = commandKey;
        this.delegate = delegate;
        this.exportDeprecatedMetrics = exportDeprecatedMetrics;
    }

    @Override
    public void initialize() {
        delegate.initialize();

        String circuitDoc = "Current status of circuit breaker: 1 = open, 0 = closed.";
        addGauge("is_circuit_breaker_open", circuitDoc, new Callable<Number>() {
            @Override
            public Number call() {
                return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(circuitBreaker.isOpen());
            }
        });

        String permitsDoc = "The number of executionSemaphorePermits in use right now.";
        addGauge("execution_semaphore_permits_in_use", permitsDoc, new Callable<Number>() {
            @Override
            public Number call() {
                return metrics.getCurrentConcurrentExecutionCount();
            }
        });

        String histogramLatencyTotalDoc = "Execution times for the "
                + "end-to-end execution of HystrixCommand.execute() or HystrixCommand.queue() until "
                + "a response is returned (or ready to return in case of queue(). The purpose of this "
                + "compared with the latency_execute* percentiles is to measure the cost of thread "
                + "queuing/scheduling/execution, semaphores, circuit breaker logic and other "
                + "aspects of overhead (including metrics capture itself).";

        String histogramLatencyExecDoc = "Execution times for the "
                + "HystrixCommand.run() method (on the child thread if using thread isolation).";

        final Histogram.Child histogramLatencyTotal = addHistogram("latency_total_seconds", histogramLatencyTotalDoc);
        final Histogram.Child histogramLatencyExecute = addHistogram("latency_execute_seconds", histogramLatencyExecDoc);

        // pre-allocate the counters for all possible events to have a clean start for the metrics
        final HashMap<HystrixEventType, Counter.Child> eventCounters = new HashMap<HystrixEventType, Counter.Child>();
        for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
            eventCounters.put(hystrixEventType, addCounter("event_total",
                    "event", hystrixEventType.name().toLowerCase(),
                    "terminal", Boolean.toString(hystrixEventType.isTerminal())
            ));
        }
        final Counter.Child totalCounter = exportDeprecatedMetrics ?
                addCounter("total",
                        "instead of total you should sum up all events with state \"terminal\" set to \"true\"",
                        true) : null;
        final Counter.Child errorCounter = exportDeprecatedMetrics ?
                addCounter("error_total",
                        "instead of error_total you should sum up all events with \"terminal\" set to ttrue that you consider errors",
                        true) : null;
        HystrixCommandCompletionStream.getInstance(commandKey)
                .observe()
                .subscribe(new Action1<HystrixCommandCompletion>() {
                    @Override
                    public void call(HystrixCommandCompletion hystrixCommandCompletion) {
                        /*
                         our assumptions about latency as returned by hystrixCommandCompletion:
                         # a latency of >= 0 indicates that this the execution occurred.
                         # a latency of == -1 indicates that the execution didn't occur (default in execution result)
                         # a latency of < -1 indicates some clock problems.

                         We will only count executions, and ignore non-executions with a value of -1.
                         Latencies of < -1 are ignored as they will decrement the counts, and Prometheus will
                         take this as a reset of the counter, therefore this should be avoided by all means.
                         */
                        long totalLatency = hystrixCommandCompletion.getTotalLatency();
                        if (totalLatency >= 0) {
                            histogramLatencyTotal.observe(totalLatency / 1000d);
                        } else if (totalLatency < -1) {
                            LOG.warn("received negative totalLatency, event not counted. " +
                                            "This indicates a clock skew? {}",
                                    hystrixCommandCompletion);
                        }
                        long executionLatency = hystrixCommandCompletion.getExecutionLatency();
                        if (executionLatency >= 0) {
                            histogramLatencyExecute.observe(executionLatency / 1000d);
                        } else if (executionLatency < -1) {
                            LOG.warn("received negative executionLatency, event not counted. " +
                                            "This indicates a clock skew? {}",
                                    hystrixCommandCompletion);
                        }
                        for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
                            int count = hystrixCommandCompletion.getEventCounts().getCount(hystrixEventType);
                            if (count > 0) {
                                if (exportDeprecatedMetrics) {
                                    switch (hystrixEventType) {
                                        /* this list is derived from {@link HystrixCommandMetrics.HealthCounts.plus} */
                                        case FAILURE:
                                        case TIMEOUT:
                                        case THREAD_POOL_REJECTED:
                                        case SEMAPHORE_REJECTED:
                                            errorCounter.inc(count);
                                        case SUCCESS:
                                            totalCounter.inc(count);
                                            break;
                                    }
                                }
                                eventCounters.get(hystrixEventType).inc(count);
                            }
                        }
                    }
                });

        if (exportProperties) {
            String propDesc = "These informational metrics report the "
                    + "actual property values being used by the HystrixCommand. This is useful to "
                    + "see when a dynamic property takes effect and confirm a property is set as "
                    + "expected.";

            addGauge("property_value_rolling_statistical_window_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.metricsRollingStatisticalWindowInMilliseconds().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_request_volume_threshold", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.circuitBreakerRequestVolumeThreshold().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_sleep_window_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.circuitBreakerSleepWindowInMilliseconds().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_error_threshold_percentage", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.circuitBreakerErrorThresholdPercentage().get();
                        }
                    });

            addGauge("property_value_circuit_breaker_force_open", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(properties.circuitBreakerForceOpen().get());
                        }
                    });

            addGauge("property_value_circuit_breaker_force_closed", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(properties.circuitBreakerForceClosed().get());
                        }
                    });

            addGauge("property_value_execution_timeout_in_milliseconds", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.executionTimeoutInMilliseconds().get();
                        }
                    });

            addGauge("property_value_execution_isolation_strategy", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.executionIsolationStrategy().get().ordinal();
                        }
                    });

            addGauge("property_value_metrics_rolling_percentile_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(properties.metricsRollingPercentileEnabled().get());
                        }
                    });

            addGauge("property_value_request_cache_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(properties.requestCacheEnabled().get());
                        }
                    });

            addGauge("property_value_request_log_enabled", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return HystrixPrometheusMetricsPublisherCommand.this.booleanToNumber(properties.requestLogEnabled().get());
                        }
                    });

            addGauge("property_value_execution_isolation_semaphore_max_concurrent_requests", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.executionIsolationSemaphoreMaxConcurrentRequests().get();
                        }
                    });

            addGauge("property_value_fallback_isolation_semaphore_max_concurrent_requests", propDesc,
                    new Callable<Number>() {
                        @Override
                        public Number call() {
                            return properties.fallbackIsolationSemaphoreMaxConcurrentRequests().get();
                        }
                    });
        }
    }

    private void addGauge(String metric, String helpDoc, Callable<Number> value) {
        collector.addGauge("hystrix_command", metric, helpDoc, labels, value);
    }

    private Histogram.Child addHistogram(String metric, String helpDoc) {
        return collector.addHistogram("hystrix_command", metric, helpDoc, labels);
    }

    private Counter.Child addCounter(String metric, String... additionalLabels) {
        return addCounter(metric, null, false, additionalLabels);
    }

    private Counter.Child addCounter(String metric, String message, boolean deprecated, String... additionalLabels) {
        SortedMap<String, String> labels = new TreeMap<String, String>(this.labels);
        labels.putAll(this.labels);
        Iterator<String> l = Arrays.asList(additionalLabels).iterator();
        while (l.hasNext()) {
            labels.put(l.next(), l.next());
        }
        return collector.addCounter("hystrix_command", metric,
                (deprecated ? "DEPRECATED: " : "")
                        + message
                        + (message != null ? " " : "")
                        + "These are cumulative counts since the start of the application.", labels);
    }

    private int booleanToNumber(boolean value) {
        return value ? 1 : 0;
    }
}
