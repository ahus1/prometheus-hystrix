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

import com.soundcloud.prometheus.hystrix.util.Consumer;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of a Prometheus Collector for Hystrix metrics.
 */
public class HystrixMetricsCollector extends Collector {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixMetricsCollector.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Gauge, List<Value>> gauges = new HashMap<Gauge, List<Value>>();
    private final Map<String, Histogram> histograms = new HashMap<String, Histogram>();
    private final Map<String, Counter> counters = new HashMap<String, Counter>();

    private final String namespace;
    private final Consumer<Histogram.Builder> histogramParameterizer;

    private final CollectorRegistry registry = new CollectorRegistry();

    public HystrixMetricsCollector(String namespace, Consumer<Histogram.Builder> histogramParameterizer) {
        this.namespace = namespace;
        this.histogramParameterizer = histogramParameterizer;
    }

    public void addGauge(String subsystem, String metric, String helpDoc,
                         SortedMap<String, String> labels, Callable<Number> value) {

        lock.writeLock().lock();
        try {
            Gauge gauge = new Gauge(name(subsystem, metric), helpDoc);
            List<Value> values = gauges.get(gauge);
            if (values == null) {
                values = new ArrayList<Value>();
                gauges.put(gauge, values);
            }
            values.add(new Value(labels, value));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Histogram.Child addHistogram(String subsystem, String metric, String helpDoc,
                                        SortedMap<String, String> labels) {
        lock.writeLock().lock();
        try {
            String name = name(subsystem, metric);
            Histogram histogram = histograms.get(name);
            if (histogram == null) {
                Histogram.Builder histogramBuilder = Histogram.build().name(name).help(helpDoc)
                        .labelNames(labels.keySet().toArray(new String[]{}));
                histogramParameterizer.accept(histogramBuilder);
                histogram = histogramBuilder.create();
                histogram.register(registry);
                histograms.put(name, histogram);
            }
            return histogram.labels(labels.values().toArray(new String[]{}));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Counter.Child addCounter(String subsystem, String metric, String helpDoc, SortedMap<String, String> labels) {
        lock.writeLock().lock();
        try {
            String name = name(subsystem, metric);
            Counter counter = counters.get(name);
            if (counter == null) {
                counter = Counter.build().name(name(subsystem, metric)).help(helpDoc).
                        labelNames(labels.keySet().toArray(new String[]{})).create();
                counter.register(registry);
                counters.put(name, counter);
            }
            return counter.labels(labels.values().toArray(new String[]{}));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String name(String subsystem, String metric) {
        return (namespace != null)
                ? namespace + "_" + subsystem + "_" + metric
                : subsystem + "_" + metric;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        lock.readLock().lock();
        try {
            List<MetricFamilySamples> samples = new LinkedList<MetricFamilySamples>();
            List<MetricFamilySamples> list = new ArrayList<MetricFamilySamples>();
            for (Map.Entry<Gauge, List<Value>> e : gauges.entrySet()) {
                MetricFamilySamples metricFamilySamples = e.getKey().toSamples(e.getValue());
                list.add(metricFamilySamples);
            }
            samples.addAll(list);
            Enumeration<MetricFamilySamples> enumeration = registry.metricFamilySamples();
            while (enumeration.hasMoreElements()) {
                samples.add(enumeration.nextElement());
            }
            return samples;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static class Gauge {

        private final String name;
        private final String helpDoc;

        public Gauge(String name, String helpDoc) {
            this.name = name;
            this.helpDoc = helpDoc;
        }

        public MetricFamilySamples toSamples(List<Value> values) {
            List<MetricFamilySamples.Sample> list = new ArrayList<MetricFamilySamples.Sample>();
            for (Value v : values) {
                MetricFamilySamples.Sample sample = v.toSample(name);
                if (sample != null) {
                    list.add(sample);
                }
            }
            return new MetricFamilySamples(name, Type.GAUGE, helpDoc, list);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj instanceof Gauge) {
                Gauge other = (Gauge) obj;
                return this.name.equals(other.name);
            }
            return false;
        }
    }

    private static class Value {

        private final List<String> labelNames;
        private final List<String> labelValues;
        private final Callable<Number> value;

        public Value(SortedMap<String, String> labels, Callable<Number> value) {
            this.labelNames = new ArrayList<String>(labels.keySet());
            this.labelValues = new ArrayList<String>(labels.values());
            this.value = value;
        }

        public MetricFamilySamples.Sample toSample(String name) {
            try {
                return new MetricFamilySamples.Sample(name, labelNames, labelValues, value.call().doubleValue());
            } catch (Exception e) {
                LOG.warn(String.format("Cannot export %s - caused by: %s", name, e), e);
                return null;
            }
        }
    }
}
