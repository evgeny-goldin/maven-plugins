package com.goldin.plugins.common

import static com.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.PluginManager
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter


/**
 * Base GroovyMojo class
 */
@SuppressWarnings( 'StatelessClass' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
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
    public    File buildDirectory
    protected File buildDirectory() { file().mkdirs( this.buildDirectory ) }

    @MojoParameter ( expression = '${project.build.outputDirectory}' )
    public    File outputDirectory
    protected File outputDirectory() { file().mkdirs( this.outputDirectory ) }


    @Override
    @Requires({ log && project && session })
    final void execute()
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
