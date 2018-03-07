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
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHookDefault;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategyDefault;
import com.soundcloud.prometheus.hystrix.util.Consumer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Histogram;

/**
 * Implementation of a {@link HystrixMetricsPublisher} for Prometheus Metrics.
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisher extends HystrixMetricsPublisher {

    private enum MetricsType {EXPONENTIAL, LINEAR, DISTINCT, DEFAULT}

    /**
     * Build an instance {@link HystrixPrometheusMetricsPublisher}. Use {@link #buildAndRegister()} to finalize.
     */
    public static class Builder {
        private String namespace = null;
        private CollectorRegistry registry = CollectorRegistry.defaultRegistry;
        private boolean exportProperties = false;
        private boolean exportDeprecatedMetrics = true;

        private MetricsType metrics = MetricsType.DEFAULT;

        private double exponentialStart = 0.001;
        private double exponentialFactor = 1.31;
        private int exponentialCount = 30;

        private double linearStart;
        private double linearWidth;
        private int linearCount;

        private double[] distinctBuckets;

        // will do this as default for release 3.3.x, might change in future releases
        private boolean registerDefaultPlugins = true;

        private Builder() {
        }

        public void buildAndRegister() {
            HystrixPlugins plugins = HystrixPlugins.getInstance();

            // memorize the registered plugins
            HystrixCommandExecutionHook commandExecutionHook = plugins.getCommandExecutionHook();
            HystrixEventNotifier eventNotifier = plugins.getEventNotifier();
            HystrixMetricsPublisher metricsPublisher = plugins.getMetricsPublisher();
            HystrixPropertiesStrategy propertiesStrategy = plugins.getPropertiesStrategy();
            HystrixConcurrencyStrategy concurrencyStrategy = plugins.getConcurrencyStrategy();

            HystrixMetricsCollector collector = new HystrixMetricsCollector(namespace,
                    new Consumer<Histogram.Builder>() {
                        @Override
                        public void accept(Histogram.Builder builder) {
                            if (metrics == MetricsType.EXPONENTIAL) {
                                builder.exponentialBuckets(exponentialStart, exponentialFactor, exponentialCount);
                            } else if (metrics == MetricsType.LINEAR) {
                                builder.linearBuckets(linearStart, linearWidth, linearCount);
                            } else if (metrics == MetricsType.DISTINCT) {
                                builder.buckets(distinctBuckets);
                            } else if (metrics == MetricsType.DEFAULT) {
                                // nothing to do
                            } else {
                                throw new IllegalStateException("unknown enum state " + metrics);
                            }
                        }
                    }).register(registry);

            // wrap the metrics publisher plugin
            HystrixPrometheusMetricsPublisher wrappedMetricsPublisher =
                    new HystrixPrometheusMetricsPublisher(exportProperties,
                            exportDeprecatedMetrics, collector, metricsPublisher);

            // reset all plugins
            HystrixPlugins.reset();
            // the following statement wouldn't be necessary, but I'm paranoid that reset might
            // change the plugin instance.
            plugins = HystrixPlugins.getInstance();

            // set previous values for all plugins, but not if they would use the default implementation,
            // as this would block the slot for plugins to be registered.

            // REASON: if there was no previous setting, Hystrix would have returned the default implementation
            // and not all other plugin use the reset-and-wrap approach we do here.

            // ASSUMPTION: the default strategies/hooks can't wrap a different strategy/hook

            // CAVEAT: instead of a default implementation there is a sophisticated Archaius configuration mechanism
            // to determine a class from property settings. There is a corner case where someone would register a
            // default implementation manually overriding an Archaius configuration. Therefore this is configurable
            // using "registerDefaultPlugins".

            if (registerDefaultPlugins || concurrencyStrategy.getClass() != HystrixConcurrencyStrategyDefault.class) {
                plugins.registerConcurrencyStrategy(concurrencyStrategy);
            }
            if (registerDefaultPlugins || commandExecutionHook.getClass() != HystrixCommandExecutionHookDefault.class) {
                plugins.registerCommandExecutionHook(commandExecutionHook);
            }
            if (registerDefaultPlugins || eventNotifier.getClass() != HystrixEventNotifierDefault.class) {
                plugins.registerEventNotifier(eventNotifier);
            }
            if (registerDefaultPlugins || propertiesStrategy.getClass() != HystrixPropertiesStrategyDefault.class) {
                plugins.registerPropertiesStrategy(propertiesStrategy);
            }

            // ... except for the metrics publisher that will now be wrapped
            plugins.registerMetricsPublisher(wrappedMetricsPublisher);
        }

        public Builder withNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder withRegistry(CollectorRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder shouldExportProperties(boolean exportProperties) {
            this.exportProperties = exportProperties;
            return this;
        }

        public Builder shouldExportDeprecatedMetrics(boolean exportDeprecatedMetrics) {
            this.exportDeprecatedMetrics = exportDeprecatedMetrics;
            return this;
        }

        public Builder shouldRegisterDefaultPlugins(boolean registerDefaultPlugins) {
            this.registerDefaultPlugins = registerDefaultPlugins;
            return this;
        }

        public Builder withExponentialBuckets(double start, double factor, int count) {
            this.metrics = MetricsType.EXPONENTIAL;
            this.exponentialStart = start;
            this.exponentialFactor = factor;
            this.exponentialCount = count;
            return this;
        }

        public Builder withBuckets(double... buckets) {
            this.metrics = MetricsType.DISTINCT;
            this.distinctBuckets = buckets;
            return this;
        }

        public Builder withLinearBuckets(double start, double width, int count) {
            this.metrics = MetricsType.LINEAR;
            this.linearStart = start;
            this.linearWidth = width;
            this.linearCount = count;
            return this;
        }

        public Builder withExponentialBuckets() {
            this.metrics = MetricsType.EXPONENTIAL;
            return this;
        }

        public Builder withDefaultBuckets() {
            this.metrics = MetricsType.DEFAULT;
            return this;
        }
    }

    public static HystrixPrometheusMetricsPublisher.Builder builder() {
        return new Builder();
    }

    private final HystrixMetricsCollector collector;
    private final boolean exportProperties;
    private final HystrixMetricsPublisher metricsPublisherDelegate;
    private final boolean exportDeprecatedMetrics;

    private HystrixPrometheusMetricsPublisher(boolean exportProperties,
                                              boolean exportDeprecatedMetrics,
                                              HystrixMetricsCollector collector,
                                              HystrixMetricsPublisher metricsPublisherDelegate) {
        this.collector = collector;
        this.exportProperties = exportProperties;
        this.exportDeprecatedMetrics = exportDeprecatedMetrics;
        this.metricsPublisherDelegate = metricsPublisherDelegate;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(
            HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties) {
        HystrixMetricsPublisherCommand delegate = metricsPublisherDelegate.getMetricsPublisherForCommand(commandKey,
                commandGroupKey, metrics, circuitBreaker, properties);

        return new HystrixPrometheusMetricsPublisherCommand(
                collector, commandKey, commandGroupKey, metrics, circuitBreaker, properties, exportProperties,
                exportDeprecatedMetrics, delegate);
    }

    @Override
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(
            HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics,
            HystrixThreadPoolProperties properties) {
        HystrixMetricsPublisherThreadPool delegate = metricsPublisherDelegate.getMetricsPublisherForThreadPool(
                threadPoolKey, metrics, properties);

        return new HystrixPrometheusMetricsPublisherThreadPool(
                collector, threadPoolKey, metrics, properties, exportProperties, delegate);
    }

    @Override
    public HystrixMetricsPublisherCollapser getMetricsPublisherForCollapser(
            HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics,
            HystrixCollapserProperties properties) {
        HystrixMetricsPublisherCollapser delegate = metricsPublisherDelegate.getMetricsPublisherForCollapser(
                collapserKey, metrics, properties);

        return new HystrixPrometheusMetricsPublisherCollapser(
                collector, collapserKey, metrics, properties, exportProperties, delegate);
    }

    /**
     * Register an instance of this publisher, without a namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will register metrics with the default CollectorRegistry
     * and will NOT attempt to export properties.
     *
     * @see CollectorRegistry#defaultRegistry
     */
    public static void register() {
        HystrixPrometheusMetricsPublisher.builder()
                .buildAndRegister();
    }

    /**
     * Register an instance of this publisher, without a namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will NOT attempt to export properties.
     */
    public static void register(CollectorRegistry registry) {
        HystrixPrometheusMetricsPublisher.builder()
                .withRegistry(registry)
                .buildAndRegister();
    }

    /**
     * Register an instance of this publisher, for the given namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will register metrics with the default CollectorRegistry
     * and will NOT attempt to export properties.
     *
     * @see CollectorRegistry#defaultRegistry
     */
    public static void register(String namespace) {
        HystrixPrometheusMetricsPublisher.builder()
                .withNamespace(namespace)
                .buildAndRegister();
    }

    /**
     * Register an instance of this publisher, for the given namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will NOT attempt to export properties.
     */
    public static void register(String namespace, CollectorRegistry registry) {
        HystrixPrometheusMetricsPublisher.builder()
                .withNamespace(namespace)
                .withRegistry(registry)
                .buildAndRegister();
    }

    /**
     * Register an instance of this publisher, for the given namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton.
     * Please use the builder instead of this method with so many parameters.
     */
    @Deprecated
    public static void register(String namespace, CollectorRegistry registry, boolean exportProperties,
                                boolean exportDeprecatedMetrics) {
        HystrixPrometheusMetricsPublisher.builder()
                .withNamespace(namespace)
                .withRegistry(registry)
                .shouldExportProperties(exportProperties)
                .shouldExportDeprecatedMetrics(exportDeprecatedMetrics)
                .buildAndRegister();
    }
}
