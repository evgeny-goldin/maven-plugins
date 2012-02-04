package com.goldin.plugins.ivy

import com.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.project.MavenProject
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Plugin that delegates artifacts resolving to Ivy.
 */
@MojoGoal ( 'generate' )
@MojoPhase ( 'compile' )
class IvyMojo extends BaseGroovyMojo
{

    @Override
    void doExecute ()
    {
        MavenProject project
    }
}
