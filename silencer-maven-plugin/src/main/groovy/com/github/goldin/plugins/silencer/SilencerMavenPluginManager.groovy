package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.*
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
            mojo.log = SilencerMojo.SILENT_LOGGER
            mojo.pluginContext[ BaseGroovyMojo.SILENT_GCOMMONS_FLAG ] = true
        }
        catch ( Throwable e ){ e.printStackTrace() }

        mojo
    }
}
