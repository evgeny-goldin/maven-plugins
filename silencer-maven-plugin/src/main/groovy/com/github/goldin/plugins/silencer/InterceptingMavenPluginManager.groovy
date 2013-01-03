package com.github.goldin.plugins.silencer

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginConfigurationException
import org.apache.maven.plugin.PluginContainerException
import org.codehaus.plexus.logging.AbstractLogEnabled
import org.gcontracts.annotations.Requires
import java.lang.reflect.Field
import org.apache.maven.plugin.Mojo


/**
 * Delegates to {@link MavenPluginManager} and updates mojos resolved.
 */
class InterceptingMavenPluginManager
{
    private final SilencerMojo              parentMojo
    @Delegate
    private final MavenPluginManager        delegate
    private final Map<String, List<String>> loggerFieldsMap


    @Requires({ parentMojo && delegate })
    InterceptingMavenPluginManager ( SilencerMojo parentMojo, MavenPluginManager delegate )
    {
        this.parentMojo      = parentMojo
        this.delegate        = delegate
        final loggerFields   = parentMojo.defaultLoggerFields + '\n' + ( parentMojo.loggerFields ?: '' )
        this.loggerFieldsMap = readLines( loggerFields ).inject( [:].withDefault{ [] } ){
            Map m, String line ->

            def ( String className, String fieldsPath ) = line.tokenize( ':' )
            assert className && fieldsPath
            m[ className ] << fieldsPath
            m
        }
    }


    @Override
    public <T> T getConfiguredMojo ( Class<T> mojoInterface, MavenSession session, MojoExecution mojoExecution )
        throws PluginConfigurationException,
               PluginContainerException
    {
        assert Mojo.isAssignableFrom( mojoInterface )
        final  Mojo mojo = ( Mojo ) delegate.getConfiguredMojo( mojoInterface, session, mojoExecution )
        assert mojo, "Failed to retrieve Mojo [${ mojoInterface.name }]"

        parentMojo.tryIt { updateLoggerFields( mojo )}
        parentMojo.tryIt { updateAbstractLogEnabledFields( mojo )}
        parentMojo.tryIt { updateSurefireMojo( mojo )}
        parentMojo.tryIt { mojo.log = parentMojo.silentLogger }
        parentMojo.tryIt { mojo.pluginContext[ BaseGroovyMojo.SILENCE ] = true }

        return ( T ) (( parentMojo.logTime || parentMojo.logSummary ) ?
            new InterceptingLoggingMojo( parentMojo, mojo, mojoExecution ) :
            mojo )
    }


    @Requires({ mojo })
    void updateLoggerFields ( Mojo mojo )
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
    void updateAbstractLogEnabledFields ( Mojo mojo )
    {
        final List<Field> mojoFields = []
        for ( Class c = mojo.class; ( c != Object ); c = c.superclass ){ mojoFields.addAll( c.declaredFields )}

        mojoFields.
        collect { Field  f     -> parentMojo.getFieldValue( mojo, f.name )}.
        findAll { Object value -> AbstractLogEnabled.isInstance( value )}.
        each    { Object value -> parentMojo.setFieldValue( value, 'logger', parentMojo.silentLogger )}
    }


    @Requires({ mojo })
    void updateSurefireMojo ( Mojo mojo )
    {
        if ( mojo.class.name == 'org.apache.maven.plugin.surefire.SurefirePlugin' )
        {
            parentMojo.setFieldValue( mojo, 'useFile',                  true   )
            parentMojo.setFieldValue( mojo, 'redirectTestOutputToFile', true   )
            parentMojo.setFieldValue( mojo, 'printSummary',             false  )
        }
    }
}
