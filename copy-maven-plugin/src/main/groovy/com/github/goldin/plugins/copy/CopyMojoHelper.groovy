package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.shared.artifact.filter.collection.*
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.deployment.DeployRequest
import org.sonatype.aether.graph.DependencyNode
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.DefaultRepositorySystemSession
import org.sonatype.aether.util.graph.selector.ScopeDependencySelector
import java.util.jar.Attributes
import java.util.jar.Manifest


/**
 * {@link CopyMojo} helper class.
 *
 * Class is marked "final" as it's not meant for subclassing.
 * Methods are marked as "protected" to allow package-access only.
 */
@SuppressWarnings( 'FinalClassWithProtectedMember' )
final class CopyMojoHelper
{
    private final BaseGroovyMojo mojo

    @Requires({ mojo })
    CopyMojoHelper ( BaseGroovyMojo mojo )
    {
        this.mojo = mojo
    }


    /**
     * Analyzes patterns specified and updates them if required:
     * - if any of them is comma or space-separated, splits it to additional patterns
     * - if any of them starts with "file:" or "classpath:", each line in the resource
     *   loaded is converted to a pattern
     *
     * @param  directory resource directory
     * @param patterns   patterns to analyze
     * @param files      encoding
     *
     * @return updated patterns list
     */
    @Requires({ encoding })
    @Ensures({ result != null })
    protected List<String> updatePatterns( String directory, List<String> patterns, String encoding )
    {
        if ( ! patterns ) { return patterns }

        patterns*.trim().grep().collect {
            String pattern ->
            pattern.startsWith( 'file:'      ) ? new File( pattern.substring( 'file:'.length())).getText( encoding ).readLines()                :
            pattern.startsWith( 'classpath:' ) ? CopyMojo.getResourceAsStream( pattern.substring( 'classpath:'.length())).readLines( encoding ) :
                                                 split( pattern )
        }.
        flatten(). // Some patterns were transferred to lines of patterns
        grep().    // Eliminating empty patterns and lines
        collect { String pattern -> ( directory && new File( directory, pattern ).directory ? "$pattern/**" : pattern )}
    }


    /**
     * Scans project dependencies, resolves and filters them using dependency provided.
     * If dependency specified is a "single" one then it is resolved normally.
     *
     * @param dependency     dependency to resolve, either "single" or "filtering" one
     * @param verbose        whether resolving process should be logged
     * @param failIfNotFound whether execution should fail if zero dependencies are resolved
     * @return               project's dependencies that passed all filters, resolved
     */
    @Requires({ dependency })
    @Ensures({ result || ( ! failIfNotFound ) })
    protected List<CopyDependency> resolveDependencies ( CopyDependency dependency, boolean verbose, boolean failIfNotFound )
    {
        /**
         * We convert GAV dependency to Maven artifact to use it later
         */
        final mavenArtifact = dependency.gav ?
            dependency.with { toMavenArtifact( groupId, artifactId, version, '', type, classifier, optional ) }:
            null

        if ( dependency.single )
        {
            dependency.artifact = mojo.resolveArtifact( mavenArtifact, verbose, failIfNotFound )
            return [ dependency ]
        }

        /**
         * Iterating over all dependencies and selecting those passing the filters.
         */

        final includeScopes = split( dependency.includeScope )
        final excludeScopes = split( dependency.excludeScope )
        final artifacts     = dependency.gav ?

            /**
             * For regular GAV dependency we collect its dependencies
             */
            collectDependencies( mavenArtifact, includeScopes, excludeScopes,
                                 dependency.transitive, dependency.includeOptional,
                                 verbose, failIfNotFound ) :
            /**
             * Otherwise, we take project's direct dependencies matching the scopes and collect their dependencies.
             * {@link org.apache.maven.project.MavenProject#getArtifacts} doesn't return transitive test dependencies
             * so we can't use it.
             */
            ( Collection<Artifact> ) mojo.project.dependencies.
            findAll {
                scopeMatches( it.scope, includeScopes, excludeScopes )
            }.
            collect { toMavenArtifact( it ) }.
            collect {
                Artifact a ->
                dependency.transitive ? collectDependencies( a, includeScopes, excludeScopes,
                                                             dependency.transitive, dependency.includeOptional,
                                                             verbose, failIfNotFound ) :
                                        a
            }.
            flatten()

        try
        {
            /**
             * When GAV coordinates appear, scope and transitivity were applied already by {@link #collectDependencies} call above.
             */
            final                filters      = composeFilters( dependency )
            List<CopyDependency> dependencies =
                eliminateDuplicates( artifacts.toSet()).
                findAll { Artifact artifact -> filters.every{ it.isArtifactIncluded( artifact ) }}.
                collect { Artifact artifact -> new CopyDependency( mojo.resolveArtifact( artifact, verbose, failIfNotFound )) }

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies [$dependency]: [${ dependencies.size() }] artifact${ generalBean().s( dependencies.size())} found" )
            if ( log.debugEnabled ) { log.debug( "Artifacts found: $dependencies" ) }

            assert ( dependencies || ( ! failIfNotFound ) || ( dependency.optional )), "No dependencies resolved with [$dependency]"
            assert dependencies.every { it.artifact?.file?.file || ( ! failIfNotFound ) || it.optional }
            dependencies
        }
        catch( e )
        {
            String errorMessage = "Failed to resolve and filter dependencies with [$dependency]"

            if ( dependency.optional || ( ! failIfNotFound ))
            {
                String exceptionMessage = e.toString()

                if ( e instanceof MultipleArtifactsNotFoundException )
                {
                    final missingArtifacts = (( MultipleArtifactsNotFoundException ) e ).missingArtifacts
                    exceptionMessage = "${ missingArtifacts.size() } missing dependenc${ missingArtifacts.size() == 1 ? 'y' : 'ies' } - $missingArtifacts"
                }

                log.warn( "$errorMessage: $exceptionMessage" )
                []
            }

            throw new MojoExecutionException( errorMessage, e )
        }
    }


    /**
     * Collects dependencies of the artifact specified.
     *
     * @param artifact            Artifact to collect dependencies of
     * @param includeScopes       dependencies scopes to include
     * @param excludeScopes       dependencies scopes to exclude
     * @param includeTransitive   whether dependencies should be traversed transitively
     * @param includeOptional     whether optional dependencies should be included
     * @param verbose             whether collecting process should be logged
     * @param failOnError         whether execution should fail if failed to collect dependencies
     * @param artifactsAggregator internal variable used by recursive iteration, shouldn't be used by caller!
     * @return                    dependencies collected (not resolved!)
     */
    @Requires({ artifact })
    @Ensures({ result != null })
    final Collection<Artifact> collectDependencies ( Artifact       artifact,
                                                     List<String>   includeScopes,
                                                     List<String>   excludeScopes,
                                                     boolean        includeTransitive,
                                                     boolean        includeOptional,
                                                     boolean        verbose,
                                                     boolean        failOnError,
                                                     Set<Artifact>  artifactsAggregator = new HashSet<Artifact>())
    {
        assert artifact.with { groupId && artifactId && version }
        if ( scopeMatches( artifact.scope, includeScopes, excludeScopes )) { artifactsAggregator << artifact }

        try
        {
            final request                  = new CollectRequest( new org.sonatype.aether.graph.Dependency( toAetherArtifact( artifact ), null ),
                                                                 mojo.remoteRepos )
            final repoSession              = mojo.repoSession
            final previousSelector         = repoSession.dependencySelector
            repoSession.dependencySelector = new ScopeDependencySelector( includeScopes, excludeScopes )

            if ( verbose )
            {
                mojo.log.info( "Collecting [$artifact] dependencies: include scopes $includeScopes, exclude scopes $excludeScopes, " +
                               "include transitive [$includeTransitive], include optional [$includeOptional]" )
            }

            final rootNode = mojo.repoSystem.collectDependencies( repoSession, request ).root
            if ( verbose ) { mojo.log.info( "Collecting [$artifact] dependencies: done" ) }

            repoSession.dependencySelector = previousSelector

            if ( ! rootNode )
            {
                assert ( ! failOnError ), "Failed to collect [$artifact] dependencies"
                return Collections.emptyList()
            }

            final childArtifacts = rootNode.children.
            findAll {
                DependencyNode childNode ->
                (( ! childNode.dependency.optional ) || includeOptional )
            }.
            collect {
                DependencyNode childNode ->
                toMavenArtifact( childNode.dependency.artifact, childNode.dependency.scope )
            }

            assert childArtifacts.every { scopeMatches( it.scope, includeScopes, excludeScopes )}

            if ( includeTransitive )
            {   /**
                 * Recursively iterating over node's children and collecting their transitive dependencies.
                 * findAll{ .. } doesn't fit here since we want to check every artifact separately before going recursive.
                 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                 * The problem we have is graph at this point containing only partial data, some of node's children
                 * may have dependencies not shown by it (I don't know why).
                 */
                childArtifacts.each {
                    if ( ! ( it in artifactsAggregator ))
                    {
                        collectDependencies( it, includeScopes, excludeScopes, includeTransitive, includeOptional,
                                             verbose, failOnError, artifactsAggregator )
                    }
                }
            }
            else
            {
                artifactsAggregator.addAll( childArtifacts )
            }

            artifactsAggregator
        }
        catch ( e )
        {
            if ( failOnError ) { throw new RuntimeException( "Failed to collect [$artifact] dependencies", e ) }
            Collections.emptyList()
        }
    }


    /**
     * Retrieves filters defined by "filtering" dependency.
     *
     * @param dependency                  "filtering" dependency
     * @param useScopeTransitivityFilters whether scope and transitivity filters should be added
     * @return filters defined by "filtering" dependency
     */
    @Requires({ dependency && ( ! dependency.single ) })
    @Ensures ({ result != null })
    private List<ArtifactsFilter> composeFilters ( CopyDependency dependency )
    {
        assert dependency && ( ! dependency.single )

        def c                         = { String s -> split( s ).join( ',' )} // Splits by "," and joins back to loose spaces
        List<ArtifactsFilter> filters = []

        if ( dependency.includeGroupIds || dependency.excludeGroupIds )
        {
            filters << new GroupIdFilter( c ( dependency.includeGroupIds ), c ( dependency.excludeGroupIds ))
        }

        if ( dependency.includeArtifactIds || dependency.excludeArtifactIds )
        {
            filters << new ArtifactIdFilter( c ( dependency.includeArtifactIds ), c ( dependency.excludeArtifactIds ))
        }

        if ( dependency.includeClassifiers || dependency.excludeClassifiers )
        {
            filters << new ClassifierFilter( c ( dependency.includeClassifiers ), c ( dependency.excludeClassifiers ))
        }

        if ( dependency.includeTypes || dependency.excludeTypes )
        {
            filters << new TypeFilter( c ( dependency.includeTypes ), c ( dependency.excludeTypes ))
        }

        filters
    }


    /**
     * Creates Manifest file in temp directory using the data specified.
     *
     * @param manifest data to store in the manifest file
     * @return temporary directory where manifest file is created according to location specified by {@code manifest} argument.
     */
    @Requires({ manifest && manifest.location && manifest.entries })
    @Ensures({ result.directory && result.listFiles() })
    File prepareManifest( CopyManifest manifest )
    {
        final m       = new Manifest()
        final tempDir = fileBean().tempDirectory()
        final f       = new File( tempDir, manifest.location )

        m.mainAttributes[ Attributes.Name.MANIFEST_VERSION ] = '1.0'
        manifest.entries.each{ String key, String value -> m.mainAttributes.putValue( key, value ) }

        fileBean().mkdirs( f.parentFile )
        f.withOutputStream { m.write( it )}

        tempDir
    }


    /**
     * Creates artifact file name, identically to
     * {@link org.apache.maven.plugin.dependency.utils.DependencyUtil#getFormattedFileName}.
     *
     * @param artifact      artifact to create the file name for
     * @param removeVersion whether version should be removed from the file name
     * @return artifact file name
     */
    @Requires({ artifact && artifact.artifactId && artifact.version })
    String artifactFileName( Artifact artifact, boolean removeVersion )
    {
        StringBuilder buffer = new StringBuilder( artifact.artifactId )

        if ( ! removeVersion )
        {
            buffer.append( "-${ artifact.version}" )
        }

        if ( artifact.classifier )
        {
            buffer.append( "-${ artifact.classifier}" )
        }

        buffer.append( ".${ artifact.type }" ).
        toString()
    }


    /**
     * Deploys file to the Maven repo specified.
     *
     * @param f          file to deploy
     * @param url        Maven repository URL
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param version    version
     * @param classifier classifier, can be <code>null</code>
     */
    @Requires({ f && f.file && url && groupId && artifactId && version })
    void deploy ( File f, String url, String groupId, String artifactId, String version, String classifier )
    {
        final description  = "[$f.canonicalPath] to [$url] as [<$groupId>:<$artifactId>:<$version>${ classifier ? ':<' + classifier + '>' : '' }]"
        final request      = new DeployRequest()
        request.repository = new RemoteRepository( url: url, type: 'default' )
        request.artifacts  = [ toAetherArtifact( toMavenArtifact( groupId, artifactId, version, '', fileBean().extension( f ), classifier, false, f )) ]

        try
        {
            mojo.repoSystem.deploy( mojo.repoSession, request )
            log.info( "Deployed $description" )
        }
        catch ( e )
        {
            throw new MojoExecutionException( "Failed to deploy $description", e )
        }
    }
}
