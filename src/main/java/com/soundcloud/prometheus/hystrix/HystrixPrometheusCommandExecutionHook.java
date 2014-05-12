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
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import io.prometheus.client.metrics.Counter;

/**
 * Decorates a <code>HystrixCommandExecutionHook</code> to count each exception that
 * results in the failure of a <code>HystrixCommand</code>.
 */
public class HystrixPrometheusCommandExecutionHook extends HystrixCommandExecutionHookDecorator {

    private static final String COMMAND_NAME = "command_name";
    private static final String COMMAND_GROUP = "command_group";
    private static final String EXCEPTION_CLASS = "exception_class";
    private static final String FAILURE_TYPE = "failure_type";

    private final Counter counter;

    public HystrixPrometheusCommandExecutionHook(String namespace, HystrixCommandExecutionHook delegate) {
        super(delegate);
        this.counter = Counter.newBuilder()
                .namespace(namespace)
                .subsystem("hystrix_command")
                .name("execution_error_count")
                .labelNames(COMMAND_GROUP, COMMAND_NAME, EXCEPTION_CLASS, FAILURE_TYPE)
                .documentation("Count of exceptions encountered by each command.")
                .build();
    }

    @Override
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        record(commandInstance, "RUN_ERROR", e);
        return super.onRunError(commandInstance, e);
    }

    @Override
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        record(commandInstance, "FALLBACK_ERROR", e);
        return super.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        record(commandInstance, failureType.name(), e);
        return super.onError(commandInstance, failureType, e);
    }

    private void record(HystrixCommand command, String failureType, Exception e) {
        counter.newPartial()
                .labelPair(COMMAND_GROUP, command.getCommandGroup().name())
                .labelPair(COMMAND_NAME, command.getCommandKey().name())
                .labelPair(EXCEPTION_CLASS, exceptionClass(e))
                .labelPair(FAILURE_TYPE, failureType)
                .apply()
                .increment();
    }

    private String exceptionClass(Exception e) {
        if (e instanceof HystrixRuntimeException && e.getCause() != null) {
            // want to track causes as much as possible, not exception wrappers
            return e.getCause().getClass().getName();
        }
        return e.getClass().getName();
    }

    /**
     * Register an instance of this excecution hook for the given namespace with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton.
     */
    public static void register(String namespace) {
        register(namespace, null);
    }

    /**
     * Register an instance of this excecution hook for the given namespace with the
     * {@link com.netflix.hystrix.strategy.HystrixPlugins} singleton and set a
     * delegate <code>HystrixCommandExecutionHook</code> to decorate.
     */
    public static void register(String namespace, HystrixCommandExecutionHook delegate) {
        HystrixPlugins.getInstance().registerCommandExecutionHook(
                new HystrixPrometheusCommandExecutionHook(namespace, delegate));
    }
}
