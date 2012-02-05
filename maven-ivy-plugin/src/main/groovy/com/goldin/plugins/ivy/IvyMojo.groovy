package com.goldin.plugins.ivy

import com.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.sonatype.aether.RepositorySystem

/**
 * Plugin that delegates artifacts resolving to Ivy.
 */
@MojoGoal ( 'add-ivy-resolver' )
@MojoPhase ( 'initialize' )
class IvyMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = true )
    public File ivyconf


    /**
     * Aether components:
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem

    @Override
    void doExecute ()
    {
        repoSystem.artifactResolver = new IvyArtifactResolver( repoSystem.artifactResolver, ivyconf )
    }
}
