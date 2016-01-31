package io.minimum.minecraft.tobench.impls;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.minimum.minecraft.tobench.EventHandler;
import io.minimum.minecraft.tobench.EventHandlerMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * A special event bus that is "frozen" once it's built.
 */
public class FrozenEventBus {

    private final Map<Class<?>, List<EventHandlerMethod>> baked;

    private FrozenEventBus(Map<Class<?>, List<EventHandlerMethod>> baked) {
        this.baked = baked;
    }

    public void post(Object event) {
        List<EventHandlerMethod> handlers = baked.get( event.getClass() );

        if ( handlers != null )
        {
            for (int i = 0; i < handlers.size(); i++) {
                EventHandlerMethod method = handlers.get(i);
                try
                {
                    method.invoke( event );
                } catch ( IllegalAccessException ex )
                {
                    throw new Error( "Method became inaccessible: " + event, ex );
                } catch ( IllegalArgumentException ex )
                {
                    throw new Error( "Method rejected target/argument: " + event, ex );
                } catch ( InvocationTargetException ex )
                {
                    //logger.log( Level.WARNING, MessageFormat.format( "Error dispatching event {0} to listener {1}", event, method.getListener() ), ex.getCause() );
                }
            }
        }
    }

    public static class Builder {
        private final Map<Class<?>, Map<Byte, Map<Object, Method[]>>> byListenerAndPriority = new HashMap<>();
        private final Map<Class<?>, EventHandlerMethod[]> byEventBaked = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        public FrozenEventBus build() {
            ImmutableMap.Builder<Class<?>, List<EventHandlerMethod>> builder = ImmutableMap.builder();
            for (Map.Entry<Class<?>, EventHandlerMethod[]> entry : byEventBaked.entrySet()) {
                builder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
            }
            return new FrozenEventBus(builder.build());
        }

        private Map<Class<?>, Map<Byte, Set<Method>>> findHandlers(Object listener)
        {
            Map<Class<?>, Map<Byte, Set<Method>>> handler = new HashMap<>();
            for ( Method m : listener.getClass().getDeclaredMethods() )
            {
                EventHandler annotation = m.getAnnotation( EventHandler.class );
                if ( annotation != null )
                {
                    Class<?>[] params = m.getParameterTypes();
                    if ( params.length != 1 )
                    {
                        continue;
                    }
                    Map<Byte, Set<Method>> prioritiesMap = handler.get( params[0] );
                    if ( prioritiesMap == null )
                    {
                        prioritiesMap = new HashMap<>();
                        handler.put( params[0], prioritiesMap );
                    }
                    Set<Method> priority = prioritiesMap.get( annotation.priority() );
                    if ( priority == null )
                    {
                        priority = new HashSet<>();
                        prioritiesMap.put( annotation.priority(), priority );
                    }
                    priority.add( m );
                }
            }
            return handler;
        }

        public void register(Object listener)
        {
            Map<Class<?>, Map<Byte, Set<Method>>> handler = findHandlers( listener );
            lock.writeLock().lock();
            try
            {
                for ( Map.Entry<Class<?>, Map<Byte, Set<Method>>> e : handler.entrySet() )
                {
                    Map<Byte, Map<Object, Method[]>> prioritiesMap = byListenerAndPriority.get( e.getKey() );
                    if ( prioritiesMap == null )
                    {
                        prioritiesMap = new HashMap<>();
                        byListenerAndPriority.put( e.getKey(), prioritiesMap );
                    }
                    for ( Map.Entry<Byte, Set<Method>> entry : e.getValue().entrySet() )
                    {
                        Map<Object, Method[]> currentPriorityMap = prioritiesMap.get( entry.getKey() );
                        if ( currentPriorityMap == null )
                        {
                            currentPriorityMap = new HashMap<>();
                            prioritiesMap.put( entry.getKey(), currentPriorityMap );
                        }
                        Method[] baked = new Method[ entry.getValue().size() ];
                        currentPriorityMap.put( listener, entry.getValue().toArray( baked ) );
                    }
                    bakeHandlers( e.getKey() );
                }
            } finally
            {
                lock.writeLock().unlock();
            }
        }

        public void unregister(Object listener)
        {
            Map<Class<?>, Map<Byte, Set<Method>>> handler = findHandlers( listener );
            lock.writeLock().lock();
            try
            {
                for ( Map.Entry<Class<?>, Map<Byte, Set<Method>>> e : handler.entrySet() )
                {
                    Map<Byte, Map<Object, Method[]>> prioritiesMap = byListenerAndPriority.get( e.getKey() );
                    if ( prioritiesMap != null )
                    {
                        for ( Byte priority : e.getValue().keySet() )
                        {
                            Map<Object, Method[]> currentPriority = prioritiesMap.get( priority );
                            if ( currentPriority != null )
                            {
                                currentPriority.remove( listener );
                                if ( currentPriority.isEmpty() )
                                {
                                    prioritiesMap.remove( priority );
                                }
                            }
                        }
                        if ( prioritiesMap.isEmpty() )
                        {
                            byListenerAndPriority.remove( e.getKey() );
                        }
                    }
                    bakeHandlers( e.getKey() );
                }
            } finally
            {
                lock.writeLock().unlock();
            }
        }

        /**
         * Shouldn't be called without first locking the writeLock; intended for use
         * only inside {@link #register(Object) register(Object)} or
         * {@link #unregister(Object) unregister(Object)}.
         */
        private void bakeHandlers(Class<?> eventClass)
        {
            Map<Byte, Map<Object, Method[]>> handlersByPriority = byListenerAndPriority.get( eventClass );
            if ( handlersByPriority != null )
            {
                List<EventHandlerMethod> handlersList = new ArrayList<>( handlersByPriority.size() * 2 );

                // Either I'm really tired, or the only way we can iterate between Byte.MIN_VALUE and Byte.MAX_VALUE inclusively,
                // with only a byte on the stack is by using a do {} while() format loop.
                byte value = Byte.MIN_VALUE;
                do
                {
                    Map<Object, Method[]> handlersByListener = handlersByPriority.get( value );
                    if ( handlersByListener != null )
                    {
                        for ( Map.Entry<Object, Method[]> listenerHandlers : handlersByListener.entrySet() )
                        {
                            for ( Method method : listenerHandlers.getValue() )
                            {
                                EventHandlerMethod ehm = new EventHandlerMethod( listenerHandlers.getKey(), method );
                                handlersList.add( ehm );
                            }
                        }
                    }
                } while ( value++ < Byte.MAX_VALUE );
                byEventBaked.put( eventClass, handlersList.toArray( new EventHandlerMethod[ handlersList.size() ] ) );
            } else
            {
                byEventBaked.put( eventClass, null );
            }
        }
    }
}
