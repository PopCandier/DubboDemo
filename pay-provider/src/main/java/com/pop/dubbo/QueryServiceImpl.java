package com.pop.dubbo;

/**
 * @author Pop
 * @date 2019/7/22 23:04
 */
public class QueryServiceImpl implements IQueryService {
    @Override
    public String getQuery(String queryString) {
        return queryString;
    }
}
