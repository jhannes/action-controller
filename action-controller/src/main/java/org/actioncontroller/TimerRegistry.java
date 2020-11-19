package org.actioncontroller;

import java.time.Duration;

/**
 * An extension point for measuring the execution of actions. E.g. use with
 * <a href="https://metrics.dropwizard.io/4.1.2/">DropWizard metrics</a> to
 * create a timer histogram for each action named
 * <code>WhateverController/whateverAction</code>:
 *
 * <pre>
 * MetricRegistry metricRegistry = new MetricRegistry()
 * TimerRegistry counterRegistry = action -> {
 *      String name = ApiServlet.class.getSimpleName() + "/" + action.getMethodName();
 *      Timer histogram = metricRegistry.timer(name);
 *      return histogram::update;
 * };
 * apiServlet.setTimerRegistry(counterRegistry);
 * </pre>
 */
public interface TimerRegistry {
    TimerRegistry NULL = action -> value -> {};

    interface Timer {
        void update(Duration duration);
    }

    Timer getTimer(ApiControllerAction action);

}
