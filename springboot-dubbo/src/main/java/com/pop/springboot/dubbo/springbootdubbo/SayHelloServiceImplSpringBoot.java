package com.pop.springboot.dubbo.springbootdubbo;

import com.pop.dubbo.ISayHelloService;
import org.apache.dubbo.config.annotation.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author Pop
 * @date 2019/7/25 0:05
 */
@Service(loadbalance = "random",cluster = "failsafe")
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        //为了好演示，这边打印出来
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("请求发来了："+call);
        return "Hello Dubbo :"+call;
    }
}
