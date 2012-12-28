package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.*
import org.gcontracts.annotations.Requires
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Modifier


/**
 * Delegates to {@link MavenPluginManager} and updates mojos resolved.
 */
class SilencerMavenPluginManager
{
    @Delegate
    private final MavenPluginManager  delegate
    private final Map<String, String> mojoFieldsMap


    @Requires({ delegate && mojoFields })
    SilencerMavenPluginManager ( MavenPluginManager delegate, String mojoFields )
    {
        this.delegate      = delegate
        this.mojoFieldsMap = mojoFields.readLines()*.trim().grep().inject( [:] ){
            Map m, String line ->

            final list     = line.tokenize( ':' )
            m[ list[ 0 ] ] = list[ 1 ]
            m
        }
    }


    public <T> T getConfiguredMojo ( Class<T> mojoInterface, MavenSession session, MojoExecution mojoExecution )
        throws PluginConfigurationException,
               PluginContainerException
    {
        final mojo = delegate.getConfiguredMojo( mojoInterface, session, mojoExecution )

        try
        {
            updateMojoFields( mojo )
            mojo.log = SilencerMojo.SILENT_LOGGER
            mojo.pluginContext[ BaseGroovyMojo.SILENT_GCOMMONS ] = true
        }
        catch ( Throwable e ){ e.printStackTrace() }

        mojo
    }


    @Requires({ mojo })
    private void updateMojoFields ( Object mojo )
    {
        final  fields = mojoFieldsMap[ mojo.class.name ]
        if ( ! fields ) { return }

        try
        {
            final  fieldsList = fields.tokenize( '.' )
            Object o          = mojo

            fieldsList.eachWithIndex { String fieldName , int j ->

                if ( j < ( fieldsList.size() - 1 ))
                {
                    o = o."$fieldName"
                }
                else
                {
                    final loggerField         = ReflectionUtils.findField( o.class, fieldName )
                    final modifiersField      = Field.class.getDeclaredField( 'modifiers' )
                    loggerField.accessible    = true
                    modifiersField.accessible = true

                    modifiersField.setInt( loggerField , loggerField.modifiers & ~Modifier.FINAL )
                    loggerField.set( o, SilencerMojo.SILENT_LOGGER )
                }
            }
        }
        catch ( Throwable e ){ e.printStackTrace() }
    }
}
