package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

/**
 * @author Alexander Schwartz 2017
 */
public class TestHystrixCommand extends HystrixCommand<Integer> {

    private final boolean shouldFail;

    public TestHystrixCommand(String key) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group_" + key)).andCommandKey(HystrixCommandKey.Factory.asKey("command_" + key)));
        shouldFail = false;
    }

    public TestHystrixCommand(String key, boolean shouldFail) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group_" + key)).andCommandKey(HystrixCommandKey.Factory.asKey("command_" + key)));
        this.shouldFail = shouldFail;
    }

    @Override
    protected Integer run() throws Exception {
        if (shouldFail) {
            throw new RuntimeException();
        }
        return 1;
    }
}
