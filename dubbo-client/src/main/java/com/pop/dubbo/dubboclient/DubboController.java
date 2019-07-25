package com.pop.dubbo.dubboclient;

import com.pop.dubbo.ISayHelloService;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Pop
 * @date 2019/7/25 22:20
 */
@RestController
public class DubboController {
    /**
     * 这里的reference注解是dubbo的注解
     * 用于注入服务
     *
     * 也是基于dubbo注册中心获取，所以还需要到propertis配置
     *
     * timeout 表示，这个请求要在 1 毫秒内请求完成，如果网络出现阻塞或者波动是很容易失败的
     * cluster 容错策略 如果你请求失败了，那希望你快点失败算了，因为他的默认是failover 重试，我们不希望他重试
     */
    @Reference(loadbalance = "random",timeout = 1,
            cluster = "failfast",mock ="com.pop.dubbo.dubboclient.SayHelloServiceMock",
        check = false)
    ISayHelloService sayHelloService;

    @GetMapping("/sayhello")
    public String sayHello() {
        return sayHelloService.sayHello("Pop");
    }
}
