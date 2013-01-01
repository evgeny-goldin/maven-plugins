package com.github.goldin.plugins.silencer

import org.apache.maven.execution.MavenSession
import org.apache.maven.lifecycle.LifecycleExecutionException
import org.apache.maven.lifecycle.internal.MojoExecutor
import org.apache.maven.lifecycle.internal.ProjectIndex
import org.apache.maven.plugin.MojoExecution
import org.gcontracts.annotations.Requires


/**
 * Intercepts {@link MojoExecutor#execute(org.apache.maven.execution.MavenSession, java.util.List, org.apache.maven.lifecycle.internal.ProjectIndex)}
 * to retrieve a list of executions
 */
class InterceptingMojoExecutor extends MojoExecutor
{
    @Delegate
    private final MojoExecutor delegate

    List<MojoExecution> mojoExecutions


    @Requires({ delegate })
    InterceptingMojoExecutor ( MojoExecutor delegate )
    {
        this.delegate = delegate
    }


    @Override
    @Requires({ session && mojoExecutions && projectIndex })
    void execute ( MavenSession session, List<MojoExecution> mojoExecutions, ProjectIndex projectIndex )
        throws LifecycleExecutionException
    {
        this.mojoExecutions = mojoExecutions
        delegate.execute( session, mojoExecutions, projectIndex )
    }
}
