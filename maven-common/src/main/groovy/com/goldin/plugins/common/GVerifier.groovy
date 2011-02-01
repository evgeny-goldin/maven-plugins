package com.goldin.plugins.common

import com.goldin.gcommons.GCommons

class GVerifier
{
    def GVerifier ()
    {
    }


    /**
     * Verifies all conditions specified are <b><code>true</code></b>
     *
     * @param conditions conditions to check
     */
    static void isTrue ( boolean ... conditions )
    {
        assert ( conditions != null )
        conditions.each{ assert it, "Condition specified is *false*" }
    }


    /**
     * Verifies all conditions specified are <b><code>false</code></b>
     *
     * @param conditions conditions to check
     */
    static void isFalse ( boolean ... conditions )
    {
        assert ( conditions != null )
        conditions.each{ assert ( ! it ), "Condition specified is *true*" }
    }


    /**
     * Verifies objects specified are <code>null</code>
     *
     * @param objects objects to check
     * @param <T> object's type
     * @return first object specified (for chaining)
     */
    static <T> T isNull ( T ... objects )
    {
        assert ( objects != null )
        objects.each{ assert ( it == null ), "Object specified *is not* null" }
        return objects[ 0 ]
    }


    /**
     * Verifies collections specified are not <code>null</code> and not empty.
     *
     * @param collections collections to check
     * @param <T>         collections' type
     *
     * @return first collection specified (for chaining)
     */
    static <T> Collection<T> notNullOrEmptyCollections ( Collection<T> ... collections )
    {
        assert ( collections != null )
        collections.each{ Collection c -> isFalse( c.isEmpty()) }
        return collections[ 0 ]
    }


    /**
     * Verifies each {@code Collection<String>} element is neither <code>null</code> nor an
     * empty <code>String</code>.
     *
     * @param collections collections to check
     * @return first collection specified (for chaining)
     */
    static Collection<String> notNullOrEmptyStrings ( Collection<String> ... collections )
    {
        assert ( collections != null )
        collections.each
        {
            Collection c ->
            c.each
            {
                String s ->
                GCommons.verify().notNullOrEmpty( s )
            }
        }

        return collections[ 0 ]
    }


    /**
     * Runs a closure verifier for files specified
     *
     * @param  caller verifier closure, it will be passed each file from arrays specified
     *         (those that don't use an "scp:" protocol)
     * @param  files files or directories to check
     * @return first file specified (for chaining)
     */
    private static File filesVerifier ( Closure caller, File ... files )
    {
        GCommons.verify().notNull( caller )
        GCommons.verify().notNull( files )

        files.each
        {
            File file ->

            if ( ! GCommons.net().isNet( file.path ))
            {
                def result = caller( file )
                assert result[ 0 ], result[ 1 ]
                //     Condition,   Error message
            }
        }

        return files[ 0 ]
    }
}