package org.actioncontroller.config;

import java.net.InetSocketAddress;

public class ConfigInetSocketAddress extends SingleValueConfigListener<InetSocketAddress> {
    private int defaultPort;

    public ConfigInetSocketAddress(String key, ConfigValueListener<InetSocketAddress> listener, int defaultPort) {
        super(key, listener, null);
        this.defaultPort = defaultPort;
    }

    @Override
    protected InetSocketAddress getDefaultValue() {
        return new InetSocketAddress(defaultPort);
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
