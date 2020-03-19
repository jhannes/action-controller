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
    protected InetSocketAddress transform(String value) {
        int colonPos = value.indexOf(':');
        if (colonPos < 0) {
            return new InetSocketAddress(Integer.parseInt(value));
        } else if (colonPos == 0) {
            return new InetSocketAddress(Integer.parseInt(value.substring(1)));
        } else {
            return new InetSocketAddress(
                    value.substring(0, colonPos),
                    Integer.parseInt(value.substring(colonPos+1))
            );
        }
    }
}
