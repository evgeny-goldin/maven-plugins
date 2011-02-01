package com.goldin.plugins.common

import org.apache.maven.plugin.logging.Log

 /**
 * {@link GMojoUtils} unit tests
 */
class GMojoUtilsTest
{
    private static final shouldFail = new GroovyTestCase().&shouldFail

    private static Log getLog () { ThreadLocals.get( Log.class ) }

    GMojoUtilsTest ()
    {
        GMojoUtils.initTestThreadLocals()
    }

    
}
