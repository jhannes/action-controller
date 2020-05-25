package org.actioncontrollerdemo;

import java.security.Principal;

public class DemoPrincipal implements Principal {
    private DemoUser demoUser;

    public DemoPrincipal(DemoUser demoUser) {
        this.demoUser = demoUser;
    }

    public static Principal createPrincipal(String username) {
        DemoUser user = new DemoUser(username);
        return user.getUsername().equals("admin") ? new AdminPrincipal(user) : new DemoPrincipal(user);
    }

    @Override
    public String getName() {
        return demoUser.getUsername();
    }
}
