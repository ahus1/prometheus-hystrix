package com.soundcloud.prometheus.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;

/**
 * @author Alexander Schwartz 2017
 */
public class TestHystrixCommand extends HystrixCommand<Integer> {

    private boolean willFail;
    private Long willWait;

    public TestHystrixCommand(String key) {
        this(key, null);
    }

    public TestHystrixCommand(String key, HystrixCommandProperties.Setter commandProperties) {
        this(
                Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("group_" + key))
                        .andCommandKey(HystrixCommandKey.Factory.asKey("command_" + key))
                        .andCommandPropertiesDefaults(commandProperties)
        );
    }

    public TestHystrixCommand(Setter setter) {
        super(setter);
    }

    public TestHystrixCommand willFail() {
        this.willFail = true;
        return this;
    }

    public TestHystrixCommand willWait(long millis) {
        this.willWait = millis;
        return this;
    }

    @Override
    protected Integer run() throws Exception {
        if (willFail) {
            throw new RuntimeException();
        }
        if (willWait != null) {
            Thread.sleep(willWait);
        }
        return 1;
    }
}
