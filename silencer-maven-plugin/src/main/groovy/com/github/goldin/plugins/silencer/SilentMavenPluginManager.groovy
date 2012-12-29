package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginConfigurationException
import org.apache.maven.plugin.PluginContainerException
import org.gcontracts.annotations.Requires


/**
 * Delegates to {@link MavenPluginManager} and updates mojos resolved.
 */
class SilentMavenPluginManager
{
    private final SilencerMojo mojo

    @Delegate
    private final MavenPluginManager  delegate
    private final Map<String, List<String>> loggerFieldsMap


    @Requires({ mojo && delegate && loggerFields })
    SilentMavenPluginManager ( SilencerMojo mojo, MavenPluginManager delegate, String loggerFields )
    {
        this.mojo            = mojo
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

        this.mojo.tryIt { updateLoggerFields( mojo ) }
        this.mojo.tryIt { mojo.log = this.mojo.silentLogger }
        this.mojo.tryIt { mojo.pluginContext[ BaseGroovyMojo.SILENCE ] = true }

        if ( mojo.class.name == 'org.apache.maven.plugin.surefire.SurefirePlugin' )
        {
            this.mojo.setFieldValue( mojo, Object.class, 'useFile',                  true   )
            this.mojo.setFieldValue( mojo, Object.class, 'printSummary',             false  )
            this.mojo.setFieldValue( mojo, Object.class, 'redirectTestOutputToFile', true   )
            this.mojo.setFieldValue( mojo, Object.class, 'reportFormat',             'none' )
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

            fieldsList.eachWithIndex { String fieldName , int j ->

                if ( j < ( fieldsList.size() - 1 ))
                {   // o.fieldA.fieldB...
                    o = this.mojo.getFieldValue( o, Object, fieldName )
                }
                else
                {   // o.loggerField
                    this.mojo.setFieldValue( o, Object, fieldName, this.mojo.silentLogger )
                }
            }
        }
    }
}
