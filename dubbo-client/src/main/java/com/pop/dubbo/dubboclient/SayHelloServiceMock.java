package com.pop.dubbo.dubboclient;

import com.pop.dubbo.ISayHelloService;

/**
 * @author Pop
 * @date 2019/7/25 23:38
 */
public class SayHelloServiceMock implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        return " 服务器发生异常，返回兜底数据。";
    }
}
