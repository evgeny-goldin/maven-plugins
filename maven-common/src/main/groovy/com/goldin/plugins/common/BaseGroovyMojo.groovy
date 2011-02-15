package com.goldin.plugins.common

import com.goldin.gcommons.GCommons
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.PluginManager
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter

 /**
 * Base GroovyMojo class
 */
abstract class BaseGroovyMojo extends GroovyMojo
{
    @MojoParameter ( expression = '${project}', required = true )
    public MavenProject mavenProject

    @MojoParameter ( expression = '${session}', required = true )
    public MavenSession mavenSession

    @MojoParameter ( expression = '${project.basedir}', required = true )
    public File basedir

    @MojoParameter ( defaultValue = '${localRepository}' )
    public ArtifactRepository localRepository

    @MojoComponent
    public PluginManager pluginManager

    @MojoParameter
    public String  runIf

    @MojoParameter ( expression = '${project.build.directory}', required = true )
    public File buildDirectory
    public File buildDirectory() { GCommons.file().mkdirs( this.buildDirectory ); this.buildDirectory }

    @MojoParameter ( expression = '${project.build.outputDirectory}' )
    public File outputDirectory
    public File outputDirectory() { GCommons.file().mkdirs( this.outputDirectory ); this.outputDirectory }


    @Override
    public void execute()
    {
        GMojoUtils.mopInit()
        ThreadLocals.set( log, mavenProject, mavenSession )
        if ( ! GMojoUtils.runIf( runIf )) { return }

        doExecute()
    }


    /**
     * {@link #execute()} replacement to be overridden by subclasses
     */
    abstract void doExecute()
}
