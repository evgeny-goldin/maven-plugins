package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.artifact.Artifact
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.codehaus.gmaven.mojo.GroovyMojo
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.resolution.DependencyRequest
import org.sonatype.aether.util.filter.ScopeDependencyFilter



/**
 * Base GroovyMojo class
 */
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
abstract class BaseGroovyMojo extends GroovyMojo
{
    protected final String  os        = System.getProperty( 'os.name' ).toLowerCase()
    protected final boolean isWindows = os.contains( 'windows' )
    protected final boolean isLinux   = os.contains( 'linux' )
    protected final boolean isMac     = os.contains( 'mac os' )


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

    /**
     * Aether components:
     * http://www.sonatype.org/aether/
     * http://eclipse.org/aether/
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem

    @MojoParameter ( defaultValue = '${repositorySystemSession}', readonly = true )
    public RepositorySystemSession repoSession

    @MojoParameter ( defaultValue = '${project.remoteProjectRepositories}', readonly = true )
    public List<RemoteRepository> remoteRepos


    /**
     * Resolves local {@link File} of Maven {@link Artifact} and updates it.
     *
     * @param artifact    Maven artifact to resolve
     * @param failOnError whether execution should fail if failed to resolve an artifact
     * @return            same artifact with its local file set
     *
     * @throws RuntimeException if 'failOnError' is true and resolution fails
     */
    @Requires({ a })
    @Ensures({ result.is( a ) })
    protected final Artifact resolveArtifact( Artifact a, boolean failOnError = true )
    {
        if ( ! a.file )
        {
            final request = new ArtifactRequest( toAetherArtifact( a ), remoteRepos, null )
            try
            {
                a.file = repoSystem.resolveArtifact( repoSession, request ).artifact?.file
            }
            catch ( e )
            {
                if ( failOnError ) { throw new RuntimeException( "Failed to resolve [$a]", e ) }
            }
        }

        if ( failOnError ) { assert a.file?.file, "Failed to resolve [$a]" }
        a
    }


    /**
     * Resolves all artifacts from the scopes specified starting from those specified initially.
     *
     * @param  rootArtifacts initial artifacts to resolve transitively from
     * @param  failOnError   whether execution should fail if failed to resolve
     * @param  scopes        artifact scopes
     * @return               artifacts resolved transitively
     *
     * @throws RuntimeException if 'failOnError' is true and resolution fails
     */
    Set<Artifact> resolveArtifacts ( Collection<Artifact> rootArtifacts, boolean failOnError = true, String ... scopes )
    {
        assert rootArtifacts && scopes

        final request = new DependencyRequest (
            new CollectRequest( rootArtifacts.collect { Artifact a -> new Dependency( toAetherArtifact( a ), null ) },
                                null,
                                remoteRepos ),
            new ScopeDependencyFilter( scopes.toList(), [] )
        )

        try
        {
            repoSystem.resolveDependencies( repoSession, request ).artifactResults*.artifact.collect {
                org.sonatype.aether.artifact.Artifact a -> toMavenArtifact( a )
            }.toSet()
        }
        catch ( e )
        {
            if ( failOnError ) { throw new RuntimeException( "Failed to resolve [$rootArtifacts]", e ) }
            [].toSet()
        }
    }


    @Override
    @Requires({ log && project && session })
    final void execute()
    {
        final  mavenVersion = mavenVersion()
        assert mavenVersion.startsWith( '3' ), "Only Maven 3 is supported, current Maven version is [$mavenVersion]"

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
