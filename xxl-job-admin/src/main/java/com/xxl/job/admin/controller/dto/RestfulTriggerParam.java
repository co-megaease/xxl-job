package com.xxl.job.admin.controller.dto;

/**
 * @author jason
 * @date 2021/9/18
 */
public class RestfulTriggerParam {

    private Integer id;

    private String executorParam;

    private String addressList;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getExecutorParam() {
        return executorParam;
    }

    public void setExecutorParam(String executorParam) {
        this.executorParam = executorParam;
    }

    public String getAddressList() {
        return addressList;
    }

    public void setAddressList(String addressList) {
        this.addressList = addressList;
    }
}
