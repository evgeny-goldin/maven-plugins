package com.goldin.plugins.common

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.gcommons.GCommons
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.PluginManager
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter
import com.goldin.gcommons.beans.*


/**
 * Base GroovyMojo class
 */
@SuppressWarnings( 'StatelessClass' )
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
    protected File buildDirectory() { fileBean().mkdirs( this.buildDirectory ) }

    @MojoParameter ( expression = '${project.build.outputDirectory}' )
    public    File outputDirectory
    protected File outputDirectory() { fileBean().mkdirs( this.outputDirectory ) }


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


    ConstantsBean constantsBean (){ GCommons.constants ()}
    GeneralBean   generalBean ()  { GCommons.general   ()}
    FileBean      fileBean ()     { GCommons.file      ()}
    NetBean       netBean ()      { GCommons.net       ()}
    IOBean        ioBean ()       { GCommons.io        ()}
    VerifyBean    verifyBean ()   { GCommons.verify    ()}
    GroovyBean    groovyBean ()   { GCommons.groovy    ()}
}
