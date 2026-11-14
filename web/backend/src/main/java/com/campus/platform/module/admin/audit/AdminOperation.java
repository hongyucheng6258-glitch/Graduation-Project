package com.campus.platform.module.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOperation {
    AdminPermission permission();
    String module();
    String action();
    int idArgument() default -1;
    boolean assistant() default false;
}
