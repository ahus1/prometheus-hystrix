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

import io.prometheus.client.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PrometheusMetricsCollector extends Collector {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsCollector.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Gauge, List<Value>> gauges = new HashMap<>();

    private final String namespace;

    public PrometheusMetricsCollector(String namespace) {
        this.namespace = namespace;
    }

    public void addGauge(String subsystem, String metric, String documentation,
                         Map<String, String> labels, Callable<Number> value) {

        lock.writeLock().lock();
        try {
            String name = namespace + "_" + subsystem + "_" + metric;
            Gauge gauge = new Gauge(name, documentation);
            List<Value> values = gauges.get(gauge);
            if (values == null) {
                values = new ArrayList<>();
                gauges.put(gauge, values);
            }
            values.add(new Value(labels, value));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<MetricFamilySamples> collect() {
        lock.readLock().lock();
        try {
            List<MetricFamilySamples> samples = new ArrayList<>(gauges.size());
            for (Map.Entry<Gauge, List<Value>> entry : gauges.entrySet()) {
                samples.add(entry.getKey().toSamples(entry.getValue()));
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
            List<MetricFamilySamples.Sample> samples = new ArrayList<>(values.size());
            for (Value value : values) {
                MetricFamilySamples.Sample sample = value.toSample(name);
                if (sample != null) {
                    samples.add(sample);
                }
            }
            return new MetricFamilySamples(name, Type.GAUGE, helpDoc, samples);
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

        public Value(Map<String, String> labels, Callable<Number> value) {
            TreeMap<String, String> map = new TreeMap<>(labels);
            this.labelNames = new ArrayList<>(map.keySet());
            this.labelValues = new ArrayList<>(map.values());
            this.value = value;
        }

        public MetricFamilySamples.Sample toSample(String name) {
            try {
                return new MetricFamilySamples.Sample(name, labelNames, labelValues, value.call().doubleValue());
            } catch (Exception e) {
                LOG.warn(String.format("Cannot export %s gauge - caused by: %s", name, e), e);
                return null;
            }
        }
    }
}
