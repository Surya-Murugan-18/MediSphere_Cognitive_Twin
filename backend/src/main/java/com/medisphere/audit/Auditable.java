package com.medisphere.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit logging via AuditAspect.
 *
 * Example:
 *   {@code @Auditable(action = "Approved Care Plan", module = "Care Plans")}
 *   public CarePlanResponse approveCarePlan(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Human-readable action description written to the audit log. */
    String action();

    /** Platform module name (e.g. "Authentication", "Care Plans", "Alerts"). */
    String module();

    /**
     * Optional Spring Expression Language (SpEL) expression to extract patientId
     * from method arguments.
     * Example: {@code patientParam = "#patientId"} or {@code "#request.patientId"}
     */
    String patientParam() default "";
}
