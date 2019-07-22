package com.pop.dubbo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 这个看做是调用支付接口的服务
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        ClassPathXmlApplicationContext context =
                new ClassPathXmlApplicationContext(
                        new String[]{"META-INF/spring/application.xml"}
                );
        context.start();
        IPayService iPayService = (IPayService) context.getBean("payService");
        System.out.println( iPayService.pay("Pop"));
    }
}
