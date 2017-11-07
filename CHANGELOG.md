# v3.4.0 - unreleased

* support JDK6 and JDK7 as both are still supported by Prometheus and Hystrix as well #16
* don't automatically register default plugins #14

# v3.3.1 - 30 October 2017

* non-executed commands (i.e. due to active circuit breakers) should not update histograms (#12)

# v3.3.0 - 02 October 2017

* Command Histograms can now be configured, and the buckets are now the default library buckets as provided by Hystrix (.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10) (#9)
* The publisher can now be configured using a builder pattern using `HystrixPrometheusMetricsPublisher.builder()/* ... */.buildAndRegister()`
* Rename _rolling_max_active_threads_ to _rolling_active_threads_max_, _rolling_count_threads_executed_ to _rate(threads_executed_total)_, _count_threads_executed_ to _threads_executed_total_.

# v3.2.0 - 09 August 2017

* Aiming to publish the command metrics Prometheus style (#4):

    * histograms _hystrix_command_event_total_ and _hystrix_command_latency_total_ replace existing gauges _hystrix_command_latency_execute_percentile_XXX_ and _hystrix_command_latency_execute_XXX_. 
    * counters _hystrix_command_total_ and _hystrix_command_error_total_ to replace the _hystrix_command_error_percentage_ gauge.
    * _hystrix_command_event_total_ contains per event counters with the event type as a label to replace _hystrix_command_count_XXX_.

* Co-exist with other Hystrix plugins like Spring Sleuth (#5)

# v3.1.0 - 23 July 2017

* Migrating from Gradle to Maven to have it in line with Prometheus Simple Client. 
* First release to Maven Central.
