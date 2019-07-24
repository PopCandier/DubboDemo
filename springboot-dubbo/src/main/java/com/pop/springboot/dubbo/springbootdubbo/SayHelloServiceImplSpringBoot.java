package com.pop.springboot.dubbo.springbootdubbo;

import com.pop.dubbo.ISayHelloService;
import org.apache.dubbo.config.annotation.Service;

/**
 * @author Pop
 * @date 2019/7/25 0:05
 */
@Service
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        return "Hello Dubbo :"+call;
    }
}
