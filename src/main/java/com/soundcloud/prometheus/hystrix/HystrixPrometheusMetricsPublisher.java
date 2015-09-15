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
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import io.prometheus.client.CollectorRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><a href="https://github.com/prometheus/client_java">Prometheus Java Client</a> implementation of {@link HystrixMetricsPublisher}.</p>
 * <p/>
 * <p>This class is based on <a href="https://github.com/Netflix/Hystrix/blob/master/hystrix-contrib/hystrix-codahale-metrics-publisher/src/main/java/com/netflix/hystrix/contrib/codahalemetricspublisher/HystrixCodaHaleMetricsPublisher.java">HystrixCodaHaleMetricsPublisher</a>.</p>
 * <p/>
 * <p>For a description of the hystrix metrics see the <a href="https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring">Hystrix Metrics &amp; Monitoring wiki</a>.<p/>
 */
public class HystrixPrometheusMetricsPublisher extends HystrixMetricsPublisher implements Runnable {

    private final String namespace;
    private final CollectorRegistry registry;
    private final boolean exportProperties;
    private final List<Runnable> publishers;

    public HystrixPrometheusMetricsPublisher(String namespace, CollectorRegistry registry, boolean exportProperties) {
        this.publishers = new ArrayList<Runnable>();
        this.exportProperties = exportProperties;
        this.namespace = namespace;
        this.registry = registry;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(
            HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey,
            HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixCommandProperties properties) {

        HystrixPrometheusMetricsPublisherCommand publisher = new HystrixPrometheusMetricsPublisherCommand(
                namespace, registry, commandKey, commandGroupKey, metrics, circuitBreaker, properties, exportProperties);

        publishers.add(publisher);
        return publisher;
    }

    @Override
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(
            HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics,
            HystrixThreadPoolProperties properties) {

        HystrixPrometheusMetricsPublisherThreadPool publisher = new HystrixPrometheusMetricsPublisherThreadPool(
                namespace, registry, threadPoolKey, metrics, properties, exportProperties);

        publishers.add(publisher);
        return publisher;
    }

    /**
     * Trigger all created metrics publishers to export their metrics into Prometheus.
     */
    @Override
    public void run() {
        for (Runnable publisher : publishers) {
            publisher.run();
        }
    }

    /**
     * Register an instance of this publisher for the given namespace with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will register metrics with the default CollectorRegistry
     * and will NOT attempt to export properties.
     *
     * @see CollectorRegistry#defaultRegistry
     */
    public static HystrixPrometheusMetricsPublisher register(String namespace) {
        return register(namespace, CollectorRegistry.defaultRegistry);
    }

    /**
     * Register an instance of this publisher for the given namespace with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The publisher
     * registered by this method will NOT attempt to export properties.
     */
    public static HystrixPrometheusMetricsPublisher register(String namespace, CollectorRegistry registry) {
        HystrixPrometheusMetricsPublisher publisher = new HystrixPrometheusMetricsPublisher(namespace, registry, false);
        HystrixPlugins.getInstance().registerMetricsPublisher(publisher);
        return publisher;
    }
}
