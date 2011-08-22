package com.goldin.plugins.common

import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.PluginManager
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter
import static com.goldin.plugins.common.GMojoUtils.*


/**
 * Base GroovyMojo class
 */
abstract class BaseGroovyMojo extends GroovyMojo
{
    @MojoParameter ( expression = '${project}', required = true )
    public MavenProject project

    @MojoParameter ( expression = '${session}', required = true )
    public MavenSession session

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
    public File buildDirectory() { fileBean().mkdirs( this.buildDirectory ) }

    @MojoParameter ( expression = '${project.build.outputDirectory}' )
    public File outputDirectory
    public File outputDirectory() { fileBean().mkdirs( this.outputDirectory ) }


    @Override
    public void execute()
    {
        ThreadLocals.set( log, project, session )
        mopInit()

        if ( ! runIf( runIf )) { return }

        doExecute()
    }


    /**
     * {@link #execute()} replacement to be overridden by subclasses
     */
    abstract void doExecute()
}
