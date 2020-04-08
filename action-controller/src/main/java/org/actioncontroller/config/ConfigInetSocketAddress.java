package org.actioncontroller.config;

import java.net.InetSocketAddress;

public class ConfigInetSocketAddress extends SingleValueConfigListener<InetSocketAddress> {
    private InetSocketAddress defaultValue;

    public ConfigInetSocketAddress(String key, ConfigValueListener<InetSocketAddress> listener, int defaultPort) {
        super(key, listener, null);
        defaultValue = new InetSocketAddress(defaultPort);
    }

    public ConfigInetSocketAddress(String key, ConfigValueListener<InetSocketAddress> listener, InetSocketAddress defaultValue) {
        super(key, listener, null);
        this.defaultValue = defaultValue;
    }

    @Override
    protected InetSocketAddress getDefaultValue() {
        return defaultValue;
    }

    @Override
    protected InetSocketAddress transform(String s) {
        return ConfigListener.asInetSocketAddress(s);
    }
}
