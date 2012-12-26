package com.github.goldin.plugins.silencer

import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginConfigurationException
import org.apache.maven.plugin.PluginContainerException
import org.gcontracts.annotations.Requires


/**
 *
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


    public <T> T getConfiguredMojo ( Class<T> mojoInterface , MavenSession session , MojoExecution mojoExecution )
        throws PluginConfigurationException,
               PluginContainerException
    {
        final mojo = delegate.getConfiguredMojo( mojoInterface, session, mojoExecution )

        if ( mojo.class.getDeclaredField( 'log' ))
        {
            mojo.log = new SilencerLogger()
        }

        mojo
    }
}
