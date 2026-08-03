package ai.riskvision.graveyard.aspect;

import ai.riskvision.graveyard.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(auditable)")
    public Object auditMethodCall(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        String username = getAuthenticatedUsername();
        HttpServletRequest request = getHttpServletRequest();

        String ipAddress = request != null ? request.getRemoteAddr() : "UNKNOWN";
        String endpoint = request != null ? request.getRequestURI() : joinPoint.getSignature().toShortString();
        String httpMethod = request != null ? request.getMethod() : "METHOD";

        String status = "success";
        int responseCode = 200;
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            status = "failed";
            responseCode = 500;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            try {
                auditLogService.recordEvent(
                        auditable.action(),
                        auditable.module(),
                        auditable.severity(),
                        status,
                        "Executed " + joinPoint.getSignature().getName() + " on " + endpoint,
                        username,
                        ipAddress,
                        endpoint,
                        httpMethod,
                        responseCode,
                        duration,
                        null
                );
            } catch (Exception e) {
                log.error("Failed to record audit log in aspect: {}", e.getMessage());
            }
        }
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "ANONYMOUS";
    }

    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
