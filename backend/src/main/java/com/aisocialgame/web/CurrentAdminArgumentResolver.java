package com.aisocialgame.web;

import com.aisocialgame.service.AdminAuthService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentAdminArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAdmin.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(AdminAuthService.PRINCIPAL_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (value instanceof AdminAuthService.AdminPrincipal principal
                && AdminAuthService.AUTHORITY.equals(principal.authority())) {
            return principal.username();
        }
        throw new com.aisocialgame.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "管理员登录已过期");
    }
}
