package org.actioncontroller.client;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationTargetException;

/**
 * Used to dynamically create a client implementation of a REST-ful interface defined through
 * an Action Controller annotated interface or class (requires ByteBuddy).
 *
 * <h3>Usage</h3>
 *
 * Given a Controller interface like the following:
 *
 * <pre>
*  public interface Controller {
 *    &#064;Get("/data")
 *    &#064;ContentBody String getContent(@RequestParam("filter") String filter);
 * }
 * </pre>
 *
 * The following will make a request to http://example.com/data?filter=foo and return the content as a string
 *
 * <pre>
 * String baserUrl = "http://example.com/";
 * Controller client = ApiClientProxy.create(Controller.class, new HttpURLConnectionApiClient(baseUrl));
 * String response = client.getContent("foo");
 * </pre>
 */
public class ApiClientClassProxy {
    public static <T> T create(Class<T> controllerClass, ApiClient client) {
        DynamicType.Loaded<?> type = new ByteBuddy()
                .subclass(controllerClass)
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(ApiClientProxy.createInvocationHandler(client)))
                .make()
                .load(controllerClass.getClassLoader());
        try {
            return (T) type.getLoaded().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
