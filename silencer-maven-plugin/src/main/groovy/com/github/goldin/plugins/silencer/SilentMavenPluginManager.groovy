package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginConfigurationException
import org.apache.maven.plugin.PluginContainerException
import org.codehaus.plexus.logging.AbstractLogEnabled
import org.gcontracts.annotations.Requires
import java.lang.reflect.Field


/**
 * Delegates to {@link MavenPluginManager} and updates mojos resolved.
 */
class SilentMavenPluginManager
{
    private final SilencerMojo parentMojo

    @Delegate
    private final MavenPluginManager  delegate
    private final Map<String, List<String>> loggerFieldsMap


    @Requires({ parentMojo && delegate && loggerFields })
    SilentMavenPluginManager ( SilencerMojo parentMojo, MavenPluginManager delegate, String loggerFields )
    {
        this.parentMojo      = parentMojo
        this.delegate        = delegate
        this.loggerFieldsMap = loggerFields.readLines()*.trim().grep().inject( [:].withDefault{ [] } ){
            Map m, String line ->

            def ( String className, String fieldsPath ) = line.tokenize( ':' )
            assert className && fieldsPath
            m[ className ] << fieldsPath
            m
        }
    }


    public <T> T getConfiguredMojo ( Class<T> mojoInterface, MavenSession session, MojoExecution mojoExecution )
        throws PluginConfigurationException,
               PluginContainerException
    {
        final  mojo = delegate.getConfiguredMojo( mojoInterface, session, mojoExecution )
        assert mojo

        parentMojo.tryIt { updateLoggerFields( mojo )}
        parentMojo.tryIt { updateAbstractLogEnabledFields( mojo )}
        parentMojo.tryIt { mojo.log = parentMojo.silentLogger }
        parentMojo.tryIt { mojo.pluginContext[ BaseGroovyMojo.SILENCE ] = true }

        if ( mojo.class.name == 'org.apache.maven.plugin.surefire.SurefirePlugin' )
        {
            parentMojo.setFieldValue( mojo, 'useFile',                  true   )
            parentMojo.setFieldValue( mojo, 'printSummary',             false  )
            parentMojo.setFieldValue( mojo, 'redirectTestOutputToFile', true   )
            parentMojo.setFieldValue( mojo, 'reportFormat',             'none' )
        }

        mojo
    }


    @Requires({ mojo })
    void updateLoggerFields ( Object mojo )
    {
        for ( fieldsPath in loggerFieldsMap[ mojo.class.name ] )
        {
            final  fieldsList = fieldsPath.tokenize( '.' )
            Object o          = mojo

            fieldsList.eachWithIndex { String fieldName, int j ->
                if ( o != null )
                {
                    if ( j < ( fieldsList.size() - 1 ))
                    {   // o.fieldA.fieldB...
                        o = parentMojo.getFieldValue( o, fieldName )
                    }
                    else
                    {   // o.loggerField
                        parentMojo.setFieldValue( o, fieldName, parentMojo.silentLogger )
                    }
                }
            }
        }
    }


    @Requires({ mojo })
    void updateAbstractLogEnabledFields ( Object mojo )
    {
        final List<Field> mojoFields = []
        for ( Class c = mojo.class; ( c != Object ); c = c.superclass ){ mojoFields.addAll( c.declaredFields )}

        mojoFields.
        collect { Field  f     -> parentMojo.getFieldValue( mojo, f.name )}.
        findAll { Object value -> AbstractLogEnabled.isInstance( value )}.
        each    { Object value -> parentMojo.setFieldValue( value, 'logger', parentMojo.silentLogger )}
    }
}
