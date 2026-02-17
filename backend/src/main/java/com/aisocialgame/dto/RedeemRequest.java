package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;

public class RedeemRequest {
    @NotBlank(message = "兑换码不能为空")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
