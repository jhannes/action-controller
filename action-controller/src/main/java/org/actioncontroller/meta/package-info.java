/**
 * The meta-package contains annotations used on other annotations
 * to attach behavior to the annotations. The package consists of
 *
 * <h3>Annotations</h3>
 *
 * Apply these to specific annotations to specify their effect:
 *
 * <ul>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpParameterMapping} annotates
 *         annotations used on parameters to specify how to populate the
 *         arguments in the context onf an {@link org.actioncontroller.ApiHttpExchange}
 *          (and {@link org.actioncontroller.client.ApiClientExchange})
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpReturnMapping} annotates
 *          annotations used on methods to specify how to deal with a return value
 *          in the context of an {@link org.actioncontroller.ApiHttpExchange}
 *          (and {@link org.actioncontroller.client.ApiClientExchange})
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpRouterMapping} annotates
 *         annotations used on methods to specify how to generate
 *         {@link org.actioncontroller.ApiControllerAction}s from the methods
 *     </li>
 * </ul>
 *
 * <h3>Factories</h3>
 *
 * These are used when a controller is initialized to convert the class structure
 * into a list of actions with mappers of parameters and return value.
 *
 * <ul>
 *     <li>
 *         {@link org.actioncontroller.meta.ApiControllerActionFactory} creates a {@link org.actioncontroller.ApiControllerAction}
 *         from a method
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpParameterMapperFactory} creates mappers from
 *         {@link org.actioncontroller.ApiHttpExchange} to method invocation arguments
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpReturnMapperFactory} creates mappers from
 *         method invocation return value {@link org.actioncontroller.ApiHttpExchange}
 *     </li>
 * </ul>
 *
 * <h3>Mappers</h3>
 *
 * These are used during a specific method invocation to convert from the HTTP values to the
 * actual method invocation values.
 *
 * <ul>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpParameterMapper} maps from an
 *         {@link org.actioncontroller.ApiHttpExchange} to a method invocation argument
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpReturnMapper} maps from an
 *         method invocation return value to {@link org.actioncontroller.ApiHttpExchange}
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpClientParameterMapper} maps from an
 *         method invocation arguments to {@link org.actioncontroller.client.ApiClientExchange}
 *     </li>
 *     <li>
 *         {@link org.actioncontroller.meta.HttpClientParameterMapper} maps from an
 *         {@link org.actioncontroller.client.ApiClientExchange} to a method invocation return value
 *     </li>
 * </ul>
 */
package org.actioncontroller.meta;
