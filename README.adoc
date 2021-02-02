= prometheus-hystrix-metrics-publisher

This is an implementation of a http://netflix.github.com/Hystrix/javadoc/index.html?com/netflix/hystrix/strategy/metrics/HystrixMetricsPublisher.html[HystrixMetricsPublisher]
that publishes metrics using Prometheus' https://github.com/prometheus/client_java[SimpleClient].

image:https://img.shields.io/maven-central/v/de.ahus1.prometheus.hystrix/prometheus-hystrix.svg[link=https://mvnrepository.com/artifact/de.ahus1.prometheus.hystrix/prometheus-hystrix]
image:https://github.com/ahus1/prometheus-hystrix/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master[Build Status,link=https://github.com/ahus1/prometheus-hystrix/actions?query=workflow%3A%22Java+CI+with+Maven%22+branch%3Amaster]

See the https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring[Netflix Metrics &amp; Monitoring] Wiki for more information.

_This continues the work started at SoundCloud._

The roadmap is the following:

* Adopt best practices in https://prometheus.io/docs/practices/naming/[naming metrics].

* Support ongoing development

Non-Goals:

* merge this project with https://github.com/prometheus/client_java[Prometheus Java Simple Client], as Hystrix is in maintenance mode and is no longer actively developed by Netflix, see the https://github.com/Netflix/Hystrix#hystrix-status[Status message in its GitHub repo].

To use this library you can use Java 6, 7 or 8. Later versions should work, but have not been tested yet - please create it ticket if you experience problems.

Continuous integration tests run on Java 8 only, but the build is parameterized to be Java 6 compatible.
Animal sniffer is part of the build to ensure no APIs that are not part of JDK 6 are used.

== USAGE

Include the most recent release from Maven Central in your build configuration.

[source,xml]
----
<dependency>
    <groupId>de.ahus1.prometheus.hystrix</groupId>
    <artifactId>prometheus-hystrix</artifactId>
    <version>4.x.x</version>
</dependency>
----

Register the metrics publisher for your application's namespace and the default Prometheus CollectorRegistry with Hystrix.

[source,java]
----
import com.soundcloud.prometheus.hystrix.HystrixPrometheusMetricsPublisher;

// ...

HystrixPrometheusMetricsPublisher.register();
----

Register the publisher for your application's namespace with your own Prometheus CollectorRegistry with Hystrix.

[source,java]
----
import com.soundcloud.prometheus.hystrix.HystrixPrometheusMetricsPublisher;
import io.prometheus.client.CollectorRegistry;

// ...

CollectorRegistry registry = // ...
HystrixPrometheusMetricsPublisher.builder().withRegistry(registry).buildAndRegister();
----

For all advanced options, please use the _.builder()_ like in the previous example.

== STATISTICS

Most metrics are similar to the standard Hystrix are straightforward to use.

The events emitted by each _HystrixCommand_ might need a little bit more explanation:
Each _HystrixCommand_ can create multiple events, but only one _terminal_ event.
The following provides the rate of each event type per command:

----
irate(hystrix_command_event_total [1m])
----

The following provides the percentage of the events over all commands:

----
irate(hystrix_command_event_total [1m])
/ ignoring(event) group_left
sum(irate(hystrix_command_event_total{terminal='true'} [1m]))
  by (command_group, command_name, instance, job, terminal)
----

You can add the following suffix condition to hide all events that never happened in your environment:

----
... AND hystrix_command_event_total > 0
----

== DEVELOPMENT

Run `./mvnw package` to compile, test and JARs locally.

The most recent development version (based on the _master_ branch on GitHub) is available from the Sonatype OSS Snapshot Repository.
To use it, include the following repository in your _pom.xml_.

To preview the contents in your browser, use the following link: +
https://oss.sonatype.org/content/repositories/snapshots/de/ahus1/prometheus/hystrix/prometheus-hystrix/

[source,xml]
----
<repositories>
    <repository>
        <id>snapshots-repo</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
----

== LICENSE

Copyright 2014-2017 SoundCloud, Inc., Alexander Schwartz and Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0[http://www.apache.org/licenses/LICENSE-2.0]

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
