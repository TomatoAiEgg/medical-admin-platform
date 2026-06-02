package com.smartclinic.common.web.aspectj;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import com.smartclinic.common.core.exception.ServiceException;
import com.smartclinic.common.core.utils.ServletUtils;
import com.smartclinic.common.web.config.properties.DemoProperties;

import java.util.Set;

/**
 * 演示模式切面 - 拦截写操作
 *
 * @author smartclinic
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class DemoAspect {

    private final DemoProperties demoProperties;

    /**
     * 需要拦截的 HTTP 方法
     */
    private static final Set<String> WRITE_METHODS = Set.of(
        "POST", "PUT", "DELETE", "PATCH"
    );

    /**
     * 拦截所有 Controller 的写操作
     */
    @Around("execution(* com.smartclinic..controller..*.*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 未开启演示模式，直接放行
        if (!Boolean.TRUE.equals(demoProperties.getEnabled())) {
            return point.proceed();
        }

        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return point.proceed();
        }

        String method = request.getMethod();
        String requestUri = request.getRequestURI();

        // 非写操作，放行
        if (!WRITE_METHODS.contains(method.toUpperCase())) {
            return point.proceed();
        }

        // 检查排除路径
        for (String exclude : demoProperties.getExcludes()) {
            if (match(exclude, requestUri)) {
                return point.proceed();
            }
        }

        log.info("演示模式拦截写操作: {} {}", method, requestUri);
        throw new ServiceException(demoProperties.getMessage());
    }

    /**
     * 路径匹配（支持通配符 * 和 **）
     */
    private boolean match(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (!path.startsWith(prefix)) {
                return false;
            }
            String suffix = path.substring(prefix.length());
            return suffix.indexOf('/') == -1;
        }
        return path.equals(pattern);
    }
}