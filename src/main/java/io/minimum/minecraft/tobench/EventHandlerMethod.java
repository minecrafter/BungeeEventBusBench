package io.minimum.minecraft.tobench;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class EventHandlerMethod
{
    private final Object listener;
    private final Method method;

    public EventHandlerMethod(Object listener, Method method)
    {
        this.listener = listener;
        this.method = method;
    }

    public void invoke(Object event) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        method.invoke( listener, event );
    }

    public Object getListener() {
        return listener;
    }

    public Method getMethod() {
        return method;
    }
}
