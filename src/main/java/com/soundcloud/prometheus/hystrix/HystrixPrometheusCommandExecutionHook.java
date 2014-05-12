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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import io.prometheus.client.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Extends the <code>HystrixCommandExecutionHook</code> to count each exception that
 * results in the failure of a <code>HystrixCommand</code>.</p>
 *
 * <p>As an added convenience for debugging purposes this class can optionally log each
 * exception at WARN level, with or without a stacktrace for the exception.</p>
 */
public class HystrixPrometheusCommandExecutionHook extends HystrixCommandExecutionHook {

    private static final String SUBSYSTEM = "hystrix_command";
    private static final String COMMAND_NAME = "command_name";
    private static final String COMMAND_GROUP = "command_group";
    private static final String EXCEPTION_CLASS = "exception_class";
    private static final String FAILURE_TYPE = "failure_type";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final boolean logExceptions;
    private final boolean logStackTrace;
    private final Counter counter;

    public HystrixPrometheusCommandExecutionHook(String namespace, boolean logExceptions, boolean logStackTrace) {
        this.logExceptions = logExceptions;
        this.logStackTrace = logStackTrace;
        this.counter = Counter.newBuilder()
                .namespace(namespace)
                .subsystem(SUBSYSTEM)
                .name("execution_error_count")
                .labelNames(COMMAND_GROUP, COMMAND_NAME, EXCEPTION_CLASS, FAILURE_TYPE)
                .documentation("Count of exceptions encountered by each command.")
                .build();
    }

    @Override
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        record(commandInstance, "RUN_ERROR", e);
        return e;
    }

    @Override
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        record(commandInstance, "FALLBACK_ERROR", e);
        return e;
    }

    @Override
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        record(commandInstance, failureType.name(), e);
        return e;
    }

    private void record(HystrixCommand command, String failureType, Exception e) {
        incrementCounter(command, failureType, e);
        logError(command, failureType, e);
    }

    private void incrementCounter(HystrixCommand command, String failureType, Exception e) {
        counter.newPartial()
                .labelPair(COMMAND_GROUP, command.getCommandGroup().name())
                .labelPair(COMMAND_NAME, command.getCommandKey().name())
                .labelPair(EXCEPTION_CLASS, e.getClass().getName())
                .labelPair(FAILURE_TYPE, failureType)
                .apply()
                .increment();
    }

    private void logError(HystrixCommand command, String failureType, Exception e) {
        if (logExceptions && logger.isWarnEnabled()) {
            if (logStackTrace) {
                logger.warn(String.format("%s in %s", failureType, command.getCommandKey().name()), e);
            } else {
                logger.warn(String.format("%s in %s - caused by: %s", failureType, command.getCommandKey().name(), e));
            }
        }
    }

    /**
     * Register an instance of this excecution hook for the given namespace with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton. The execution
     * hook registered by this method will NOT log any exceptions.
     */
    public static void register(String namespace) {
        HystrixPlugins.getInstance().registerCommandExecutionHook(
                new HystrixPrometheusCommandExecutionHook(namespace, false, false));
    }
}
