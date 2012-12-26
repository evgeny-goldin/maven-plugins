package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.lifecycle.internal.MojoExecutor
import org.apache.maven.plugin.BuildPluginManager
import org.apache.maven.plugin.DefaultBuildPluginManager
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.jfrog.maven.annomojo.annotations.MojoThreadSafe


/**
 * Mojo for silencing Maven logging.
 */
@MojoThreadSafe
@MojoGoal( 'silence' )
@MojoPhase( 'validate' )
class SilencerMojo extends BaseGroovyMojo
{
    @MojoComponent
    private BuildPluginManager manager

    @Override
    void doExecute ( )
    {
        final executor = session.container.lookup( MojoExecutor )
        (( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager = new SilencerMavenPluginManager( executor.mavenPluginManager )
    }
}
