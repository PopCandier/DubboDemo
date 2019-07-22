package com.pop.dubbo;

/**
 * @author Pop
 * @date 2019/7/22 21:20
 */
public interface IPayService {

    /**
     * 暴露出去的服务，
     * 完成支付的功能
     * @param info
     * @return
     */
    String pay(String info);
}
