package com.medisphere.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Parameter;

/**
 * Intercepts methods annotated with {@link Auditable} and writes an audit log
 * entry after the method completes (success or access-denied).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final ExpressionParser spel = new SpelExpressionParser();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String status = "Success";
        Throwable thrown = null;

        try {
            return joinPoint.proceed();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            status = "Denied";
            thrown = e;
            throw e;
        } catch (Throwable t) {
            // Other exceptions — still log as success (method was reached), rethrow
            thrown = t;
            throw t;
        } finally {
            writeAuditEntry(joinPoint, auditable, status);
        }
    }

    private void writeAuditEntry(ProceedingJoinPoint joinPoint, Auditable auditable, String status) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = (auth != null && auth.isAuthenticated()) ? String.valueOf(auth.getPrincipal()) : "anonymous";
            String userRole = (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty())
                    ? auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                    : "UNKNOWN";

            String patientId = extractPatientId(joinPoint, auditable.patientParam());
            String ipAddress = extractIpAddress();

            AuditContext ctx = AuditContext.builder()
                    .userId(userId)
                    .userRole(userRole)
                    .action(auditable.action())
                    .module(auditable.module())
                    .patientId(patientId)
                    .status(status)
                    .ipAddress(ipAddress)
                    .build();

            auditService.log(ctx);

        } catch (Exception e) {
            log.warn("AuditAspect failed to create audit context: {}", e.getMessage());
        }
    }

    private String extractPatientId(ProceedingJoinPoint joinPoint, String spelExpr) {
        if (!StringUtils.hasText(spelExpr)) return null;

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Parameter[] parameters = signature.getMethod().getParameters();
            Object[] args = joinPoint.getArgs();

            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < parameters.length; i++) {
                context.setVariable(parameters[i].getName(), args[i]);
            }
            Object result = spel.parseExpression(spelExpr).getValue(context);
            return result != null ? result.toString() : null;

        } catch (Exception e) {
            log.debug("Could not extract patientId via SpEL '{}': {}", spelExpr, e.getMessage());
            return null;
        }
    }

    private String extractIpAddress() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
