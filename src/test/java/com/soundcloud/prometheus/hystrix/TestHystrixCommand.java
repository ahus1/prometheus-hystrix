package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;

/**
 * @author Alexander Schwartz 2017
 */
public class TestHystrixCommand extends HystrixCommand<Integer> {

    private final boolean shouldFail;

    public TestHystrixCommand(String key) {
        this(key, false);
    }

    public TestHystrixCommand(String key, boolean shouldFail) {
        this(key, shouldFail, null);
    }

    public TestHystrixCommand(String key, boolean shouldFail, HystrixCommandProperties.Setter commandProperties) {
        this(
            Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("group_" + key))
                .andCommandKey(HystrixCommandKey.Factory.asKey("command_" + key))
                .andCommandPropertiesDefaults(commandProperties),
            shouldFail
        );
    }

    public TestHystrixCommand(Setter setter, boolean shouldFail) {
        super(setter);
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
