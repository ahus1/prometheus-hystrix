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
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import io.prometheus.client.CollectorRegistry;

/**
 * Implementation of a {@link HystrixMetricsPublisher} for Prometheus Metrics.
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics and Monitoring</a>.
 */
public class HystrixPrometheusMetricsPublisher extends HystrixMetricsPublisher {

    private final HystrixMetricsCollector collector;
    private final boolean exportProperties;
    private HystrixMetricsPublisher metricsPublisherDelegate;
    private final boolean exportDeprecatedMetrics;

    public HystrixPrometheusMetricsPublisher(String namespace, CollectorRegistry registry, boolean exportProperties,
                                             boolean exportDeprecatedMetrics,
                                             HystrixMetricsPublisher metricsPublisherDelegate) {
        this.collector = new HystrixMetricsCollector(namespace).register(registry);
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
        register(null, CollectorRegistry.defaultRegistry, false, true);
    }

    /**
     * Register an instance of this publisher, without a namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will NOT attempt to export properties.
     */
    public static void register(CollectorRegistry registry) {
        register(null, registry, false, true);
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
        register(namespace, CollectorRegistry.defaultRegistry, false, true);
    }

    /**
     * Register an instance of this publisher, for the given namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will NOT attempt to export properties.
     */
    public static void register(String namespace, CollectorRegistry registry) {
        register(namespace, registry, false, true);
    }

    /**
     * Register an instance of this publisher, for the given namespace, with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton.
     */
    public static void register(String namespace, CollectorRegistry registry, boolean exportProperties,
                                boolean exportDeprecatedMetrics) {

        // memorize the registered plugins
        HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins
                .getInstance().getCommandExecutionHook();
        HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance()
                .getEventNotifier();
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance()
                .getMetricsPublisher();
        HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance()
                .getPropertiesStrategy();
        HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance()
                .getConcurrencyStrategy();

        // wrap the metrics publisher plugin
        HystrixPrometheusMetricsPublisher wrappedMetricsPublisher =
                new HystrixPrometheusMetricsPublisher(namespace, registry, exportProperties,
                        exportDeprecatedMetrics, metricsPublisher);

        // reset all plugins
        HystrixPlugins.reset();

        // set previous values for all plugins ...
        HystrixPlugins.getInstance().registerConcurrencyStrategy(concurrencyStrategy);
        HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
        HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
        HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);

        // ... except for the metrics publisher that will now be wrapped
        HystrixPlugins.getInstance().registerMetricsPublisher(wrappedMetricsPublisher);
    }
}
