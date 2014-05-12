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
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;

/**
 * Decorator for <code>HystrixCommandExecutionHook</code> which passes all calls to a
 * given delegate or to the superclass if the delegate is null.
 */
public class HystrixCommandExecutionHookDecorator extends HystrixCommandExecutionHook {

    private final HystrixCommandExecutionHook delegate;

    public HystrixCommandExecutionHookDecorator(HystrixCommandExecutionHook delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> void onRunStart(HystrixCommand<T> commandInstance) {
        if (delegate != null) {
            delegate.onRunStart(commandInstance);
        } else {
            super.onRunStart(commandInstance);
        }
    }

    @Override
    public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
        if (delegate != null) {
            return delegate.onRunSuccess(commandInstance, response);
        } else {
            return super.onRunSuccess(commandInstance, response);
        }
    }

    @Override
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        if (delegate != null) {
            return delegate.onRunError(commandInstance, e);
        } else {
            return super.onRunError(commandInstance, e);
        }
    }

    @Override
    public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
        if (delegate != null) {
            delegate.onFallbackStart(commandInstance);
        } else {
            super.onFallbackStart(commandInstance);
        }
    }

    @Override
    public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
        if (delegate != null) {
            return delegate.onFallbackSuccess(commandInstance, fallbackResponse);
        } else {
            return super.onFallbackSuccess(commandInstance, fallbackResponse);
        }
    }

    @Override
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        if (delegate != null) {
            return delegate.onFallbackError(commandInstance, e);
        } else {
            return super.onFallbackError(commandInstance, e);
        }
    }

    @Override
    public <T> void onStart(HystrixCommand<T> commandInstance) {
        if (delegate != null) {
            delegate.onStart(commandInstance);
        } else {
            super.onStart(commandInstance);
        }
    }

    @Override
    public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
        if (delegate != null) {
            return delegate.onComplete(commandInstance, response);
        } else {
            return super.onComplete(commandInstance, response);
        }
    }

    @Override
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        if (delegate != null) {
            return delegate.onError(commandInstance, failureType, e);
        } else {
            return super.onError(commandInstance, failureType, e);
        }
    }

    @Override
    public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
        if (delegate != null) {
            delegate.onThreadStart(commandInstance);
        } else {
            super.onThreadStart(commandInstance);
        }
    }

    @Override
    public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
        if (delegate != null) {
            delegate.onThreadComplete(commandInstance);
        } else {
            super.onThreadComplete(commandInstance);
        }
    }
}
