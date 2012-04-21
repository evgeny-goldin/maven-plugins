package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoParameter


/**
 * Base GroovyMojo class
 */
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
abstract class BaseGroovyMojo extends GroovyMojo
{
    protected final boolean isWindows = System.getProperty( 'os.name' ).toLowerCase().contains( 'windows' )
    protected final boolean isLinux   = System.getProperty( 'os.name' ).toLowerCase().contains( 'linux' )
    protected final boolean isMac     = System.getProperty( 'os.name' ).toLowerCase().contains( 'mac os' )

    @MojoParameter ( required = true, expression = '${project}' )
    public MavenProject project

    @MojoParameter ( required = true, expression = '${session}' )
    public MavenSession session

    @MojoParameter ( required = true, expression = '${project.basedir}' )
    public File basedir

    @MojoParameter ( required = true, expression = '${project.build.directory}' )
    public    File buildDirectory
    protected File buildDirectory() { file().mkdirs( this.buildDirectory ) }

    @MojoParameter ( required = true, expression = '${project.build.outputDirectory}' )
    public    File outputDirectory
    protected File outputDirectory() { file().mkdirs( this.outputDirectory ) }

    @MojoParameter
    public String  runIf

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
