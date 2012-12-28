package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.apache.maven.artifact.Artifact
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.slf4j.LoggerFactory
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest


/**
 * Base GroovyMojo class
 */
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])
abstract class BaseGroovyMojo extends GroovyMojo
{
    static    final String  SILENT_GCOMMONS = 'SILENT_GCOMMONS'
    protected final String  os              = System.getProperty( 'os.name' ).toLowerCase()
    protected final boolean isWindows       = os.contains( 'windows' )
    protected final boolean isLinux         = os.contains( 'linux' )
    protected final boolean isMac           = os.contains( 'mac os' )

    @Parameter ( required = true, defaultValue = '${project}' )
    MavenProject project

    @Parameter ( required = true, defaultValue = '${session}' )
    MavenSession session

    @Parameter ( required = true, defaultValue = '${project.basedir}' )
    File basedir

    @Parameter ( required = true, defaultValue = '${project.build.outputDirectory}' )
    private   File outputDirectory
    File outputDirectory() { fileBean().mkdirs( this.outputDirectory ) }

    @Parameter
    String runIf

    /**
     * Aether components:
     * http://www.sonatype.org/aether/
     * http://eclipse.org/aether/
     * https://docs.sonatype.org/display/AETHER/Home
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     */

    @Component
    RepositorySystem repoSystem

    @Parameter ( defaultValue = '${repositorySystemSession}', readonly = true )
    RepositorySystemSession repoSession

    @Parameter ( defaultValue = '${project.remoteProjectRepositories}', readonly = true )
    List<RemoteRepository> remoteRepos


    /**
     * Resolves local {@link File} of Maven {@link Artifact} and updates it.
     *
     * @param artifact    Maven artifact to resolve
     * @param verbose     whether resolving process should be logged
     * @param failOnError whether execution should fail if failed to resolve an artifact
     * @return            same artifact with its local file set
     *
     * @throws RuntimeException if 'failOnError' is true and resolution fails
     */
    @Requires({ artifact })
    @Ensures({ result.is( artifact ) })
    final Artifact resolveArtifact( Artifact artifact, boolean verbose, boolean failOnError )
    {
        final errorMessage = "Failed to resolve ${ artifact.optional ? 'optional artifact ' : '' }[$artifact]"

        if ( ! artifact.file )
        {
            final request = new ArtifactRequest( toAetherArtifact( artifact ), remoteRepos, null )
            try
            {
                if ( verbose ) { log.info( "Resolving [$artifact]: optional [$artifact.optional], failOnError [$failOnError]" ) }
                artifact.file = repoSystem.resolveArtifact( repoSession, request ).artifact?.file
                if ( verbose ) { log.info( "Resolving [$artifact]: done - [$artifact.file]" ) }
            }
            catch ( e )
            {
                if (( ! artifact.optional ) && failOnError ) { throw new MojoExecutionException( errorMessage, e ) }
            }
        }

        if ( ! artifact.file?.file )
        {
            assert ( artifact.optional || ( ! failOnError )), errorMessage
            log.warn( errorMessage )
        }

        artifact
    }


    @Override
    @Requires({ log && project && session })
    final void execute()
    {
        if ( pluginContext[ SILENT_GCOMMONS ] ){ disableGCommonsLoggers() }

        final  mavenVersion = mavenVersion()
        assert mavenVersion.startsWith( '3' ), "Only Maven 3 is supported, current Maven version is [$mavenVersion]"

        ThreadLocals.set( log, project, session )
        mopInit()

        if ( ! runIf( runIf )) { return }

        doExecute()
    }


    private void disableGCommonsLoggers ( )
    {
        final context         = (( LoggerContext ) LoggerFactory.ILoggerFactory )
        final gcommonsLoggers = context.loggerList.findAll { it.name.with { contains( 'com.github.goldin.' ) && contains( '.gcommons' ) }}
        gcommonsLoggers.each { it.effectiveLevelInt = Level.OFF_INT }
    }


    /**
     * {@link #execute()} replacement to be overridden by subclasses
     */
    abstract void doExecute()
}
