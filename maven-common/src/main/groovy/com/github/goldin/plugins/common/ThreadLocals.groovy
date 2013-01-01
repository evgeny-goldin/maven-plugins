package com.github.goldin.plugins.common

 /**
 * {@link ThreadLocal} storage of plugin environment
 */
class ThreadLocals
{

    static class MyThreadLocal extends ThreadLocal<Map<Class<?>, ?>>
    {
        @Override
        protected Map initialValue() { [:] }
    }

    private static final ThreadLocal<Map<Class<?>, ?>> THREAD_LOCAL = new MyThreadLocal()


    /**
     * Stores objects specified in a {@link ThreadLocal} Map using their class names as key.
     * Usually invoked by the plugin upon execution to store its Maven environment,
     * like "project" and "session", to be later used by various helper methods.
     * This storage eliminates the need to pass Maven environment around as additional arguments.
     *
     * @param objects objects to store
     * @return first object stored
     */
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess', 'GroovyStaticMethodNamingConvention' ])
    static <T> T set ( T ... objects )
    {
        for ( T t in objects ) { THREAD_LOCAL.get()[ t.getClass() ] = t }
        objects[ 0 ]
    }


    /**
     * Retrieves object from a {@link ThreadLocal} Map using class name as a key.
     *
     * @param requiredClass class of object that needs to be read from the Map
     * @return object from a {@link ThreadLocal} Map using class name as a key
     */
    @SuppressWarnings([ 'GroovyStaticMethodNamingConvention' ])
    static <T> T get ( Class<T> requiredClass )
    {
        assert requiredClass
        T t = ( T ) THREAD_LOCAL.get()[ requiredClass ]

        if ( t == null )
        {
            for ( Class availableClass in THREAD_LOCAL.get().keySet())
            {
                if ( requiredClass.isAssignableFrom( availableClass ))
                {
                    t = ( T ) THREAD_LOCAL.get()[ availableClass ]
                    // noinspection GroovyBreak
                    break
                }
            }
        }

        assert ( t != null ) : \
               "ThreadLocals doesn't contain object of type [${ requiredClass.name }]. " +
               "Available objects are ${ THREAD_LOCAL.get().keySet()*.name }"
        t
    }
}
