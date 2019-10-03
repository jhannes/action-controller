/**
 * <p>The {@link org.actioncontroller.config.ConfigObserver} configuration framework monitors
 * a set of configuration files for changes and calls {@link org.actioncontroller.config.ConfigListener}s
 * with initial value and for each subsequent value.</p>
 *
 * <p>This can be used to change configuration on the fly, such as restarting a server when the
 * configured server port is updated or replacing a connection pool when the datasource connection
 * configuration is changed.</p>
 */
package org.actioncontroller.config;
