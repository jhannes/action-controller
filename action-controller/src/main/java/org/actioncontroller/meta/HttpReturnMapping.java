package org.actioncontroller.meta;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Attach return value mapping behavior to an annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpReturnMapping {

    Class<? extends HttpReturnMapperFactory> value();

}
