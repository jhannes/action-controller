package org.fakeservlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

public class FakeHttpSession implements HttpSession {

    private final HashMap<String, Object> sessionData = new HashMap<>();

    @Override
    public long getCreationTime() {
        throw unimplemented();
    }

    @Override
    public String getId() {
        throw unimplemented();
    }

    @Override
    public long getLastAccessedTime() {
        throw unimplemented();
    }

    @Override
    public ServletContext getServletContext() {
        throw unimplemented();
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        throw unimplemented();
    }

    @Override
    public int getMaxInactiveInterval() {
        throw unimplemented();
    }

    @Override
    public HttpSessionContext getSessionContext() {
        throw unimplemented();
    }

    @Override
    public Object getAttribute(String name) {
        return getValue(name);
    }

    @Override
    public Object getValue(String name) {
        return sessionData.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new Vector<>(Arrays.asList(getValueNames())).elements();
    }

    @Override
    public String[] getValueNames() {
        return sessionData.keySet().toArray(new String[0]);
    }

    @Override
    public void setAttribute(String name, Object value) {
        putValue(name, value);
    }

    @Override
    public void putValue(String name, Object value) {
        sessionData.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        removeValue(name);
    }

    @Override
    public void removeValue(String name) {
        sessionData.remove(name);
    }

    @Override
    public void invalidate() {
        Collections.list(getAttributeNames()).forEach(this::removeAttribute);
    }

    @Override
    public boolean isNew() {
        throw unimplemented();
    }

    private AssertionError unimplemented() {
        return new AssertionError("called unexpected method");
    }
}
