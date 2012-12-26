package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.lifecycle.internal.MojoExecutor
import org.apache.maven.plugin.DefaultBuildPluginManager
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo


/**
 * Mojo for silencing Maven logging.
 */
@Mojo( name = 'silence', defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
class SilencerMojo extends BaseGroovyMojo
{
    @Override
    void doExecute ( )
    {
        final executor = session.container.lookup( MojoExecutor )
        (( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager =
            new SilencerMavenPluginManager((( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager )
    }
}
