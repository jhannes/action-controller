/**
 * Action Controller is a framework that takes "controller" classes and generates
 * {@link org.actioncontroller.ApiControllerAction} for each method annotated with
 * a "routing mapper annotation" such as {@link org.actioncontroller.Get}. Example
 *
 * <pre>
 * public class TestController {
 *
 *     &#064;Get("/uppercase")
 *     &#064;ContentBody
 *     public String upcase(@RequestParam("myParam") String parameter) {
 *         return parameter.toUpperCase();
 *     }
 * }
 * </pre>
 *
 * In this example, {@link org.actioncontroller.Get} is a routing mapper annotation because it is
 * annotated {@link org.actioncontroller.meta.HttpRouterMapping}. This annotation refers to
 * {@link org.actioncontroller.Get.ActionFactory} and {@link org.actioncontroller.Get.ActionFactory#create}
 * is used to create an {@link org.actioncontroller.ApiControllerMethodAction}.
 *
 * {@link org.actioncontroller.ApiControllerMethodAction} uses the {@link org.actioncontroller.RequestParam}
 * annotation on the parameter to decide how this parameter is mapped from the request. {@link org.actioncontroller.RequestParam}
 * is annotated with {@link org.actioncontroller.meta.HttpParameterMapping}, which refers to
 * {@link org.actioncontroller.RequestParam.MapperFactory}. {@link org.actioncontroller.RequestParam.MapperFactory}
 * again is used to create a {@link org.actioncontroller.meta.HttpParameterMapper} which extracts the method argument
 * from the http request.
 *
 * {@link org.actioncontroller.ApiControllerMethodAction} further uses the {@link org.actioncontroller.ContentBody}
 * annotation on the method to determine how the return value is mapped the the response. {@link org.actioncontroller.ContentBody}
 * is annotated with {@link org.actioncontroller.meta.HttpReturnMapping}, which refers to
 * {@link org.actioncontroller.ContentBody.MapperFactory}. {@link org.actioncontroller.ContentBody.MapperFactory}
 * is used to create a {@link org.actioncontroller.meta.HttpReturnMapper} which converts the method return value to
 * the http response.
 *
 * A controller can be used in three contexts:
 * <ul>
 *     <li>With {@link org.actioncontroller.servlet.ApiServlet} to extract actions to be used in a servlet container</li>
 *     <li>With {@link org.actioncontroller.httpserver.ApiHandler} to extract actions to be used with {@link com.sun.net.httpserver.HttpServer}</li>
 *     <li>With {@link org.actioncontroller.client.ApiClientProxy} to extract actions to call a REST service via HTTP</li>
 * </ul>
 */
package org.actioncontroller;
