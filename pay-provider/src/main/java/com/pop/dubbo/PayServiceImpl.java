package com.pop.dubbo;

/**
 * @author Pop
 * @date 2019/7/22 21:20
 */
public class PayServiceImpl implements IPayService {

    @Override
    public String pay(String info) {
        System.out.println("execute play:"+info);
        return "Hello Dubbo:"+info;
    }
}
