package org.actioncontrollerdemo;

import java.security.Principal;

public class DemoPrincipal implements Principal {
    private DemoUser demoUser;

    public DemoPrincipal(DemoUser demoUser) {
        this.demoUser = demoUser;
    }

    @Override
    public String getName() {
        return demoUser.getUsername();
    }
}
