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
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.graph.DependencyNode


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
    @Requires({ artifact })
    @Ensures({ result.is( artifact ) })
    final Artifact resolveArtifact( Artifact artifact, boolean failOnError )
    {
        if ( ! artifact.file )
        {
            final request = new ArtifactRequest( toAetherArtifact( artifact ), remoteRepos, null )
            try
            {
                artifact.file = repoSystem.resolveArtifact( repoSession, request ).artifact?.file
            }
            catch ( e )
            {
                if ( failOnError ) { throw new RuntimeException( "Failed to resolve [$artifact]", e ) }
            }
        }

        if ( failOnError ) { assert artifact.file?.file, "Failed to resolve [$artifact]" }
        artifact
    }


    /**
     * Collects transitive dependencies of the artifact specified.
     *
     * @param artifact        Maven artifact to collect transitive dependencies of
     * @param scope           Artifact scope
     * @param failOnError     whether execution should fail if failed to collect dependencies
     * @param collectOptional whether optional dependencies should be excluded
     * @return                dependencies collected (not resolved!)
     *
     * @throws RuntimeException if 'failOnError' is true and collecting dependencies fails
     */
    @Requires({ artifact && ( artifact.scope != null ) })
    @Ensures({ result })
    final Collection<Artifact> collectTransitiveDependencies ( Artifact artifact,
                                                               String   scope,
                                                               boolean  failOnError,
                                                               boolean  collectOptional )
    {
        final result = [ artifact ].toSet()
        collectTransitiveDependenciesRecursively( artifact, scope, failOnError, collectOptional, result )
        result
    }


    /**
     * Collects transitive dependencies of the artifact specified.
     *
     * @param artifact           Maven artifact to collect transitive dependencies of
     * @param scope              Artifact scope
     * @param failOnError        whether execution should fail if failed to collect dependencies
     * @param collectOptional    whether optional dependencies should be excluded
     * @param artifactsInProcess Set of artifacts being processed recursively
     */
    @Requires({ artifact && ( scope != null ) && artifactsInProcess && ( artifact in artifactsInProcess ) && false })
    @Ensures({ artifactsInProcess })
    private void collectTransitiveDependenciesRecursively ( Artifact      artifact,
                                                            String        scope,
                                                            boolean       failOnError,
                                                            boolean       collectOptional,
                                                            Set<Artifact> artifactsInProcess )
    {
        final dependency = new Dependency( toAetherArtifact( artifact ) , scope )
        final request    = new CollectRequest( dependency, remoteRepos )
        def   rootNode

        try
        {
            rootNode = repoSystem.collectDependencies( repoSession, request ).root
            if ( ! rootNode )
            {
                assert ( ! failOnError ), "Failed to collect [$artifact] transitive dependencies"
                return
            }
        }
        catch ( e )
        {
            if ( failOnError ) { throw new RuntimeException( "Failed to collect [$artifact] transitive dependencies", e ) }
        }

        rootNode.children.
        findAll {
            DependencyNode childNode ->
            (( ! childNode.dependency.optional ) || collectOptional )
        }.
        collect {
            DependencyNode childNode ->
            toMavenArtifact( childNode.dependency.artifact )
        }.
        each {
            Artifact a ->

            /**
             * Every child artifact is checked before going recursive.
             * findAll{ .. } doesn't work here as it checks all child artifacts only once
             * and ignores any updates from recursive invocations.
             */
            if ( ! ( a in artifactsInProcess ))
            {
                collectTransitiveDependenciesRecursively( a, scope, failOnError, collectOptional,
                                                          (( Set ) artifactsInProcess << a ))
            }
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
