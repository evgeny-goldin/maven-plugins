package com.github.goldin.plugins.silencer

import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginConfigurationException
import org.apache.maven.plugin.PluginContainerException
import org.gcontracts.annotations.Requires


/**
 * Delegates to {@link MavenPluginManager} and updates mojos resolved.
 */
class SilencerMavenPluginManager
{
    @Delegate
    private final MavenPluginManager delegate

    @Requires({ delegate })
    SilencerMavenPluginManager ( MavenPluginManager delegate )
    {
        this.delegate = delegate
    }


    public <T> T getConfiguredMojo ( Class<T> mojoInterface, MavenSession session, MojoExecution mojoExecution )
        throws PluginConfigurationException,
               PluginContainerException
    {
        final mojo = delegate.getConfiguredMojo( mojoInterface, session, mojoExecution )

        try
        {
/*
            for ( field in mojo.class.declaredFields )
            {
                field.accessible = true
                final fieldValue = field.get( mojo )
                if ( fieldValue instanceof AbstractLogEnabled )
                {
                    (( AbstractLogEnabled ) fieldValue ).enableLogging( new SilentLogger())
                }
            }
*/

            mojo.log = SilencerMojo.SILENT_LOGGER
        }
        catch ( Throwable e ){ e.printStackTrace() }

        mojo
    }
}
