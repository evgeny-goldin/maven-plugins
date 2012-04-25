package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.shared.artifact.filter.collection.*

import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.graph.DependencyNode
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
     * @param failIfNotFound whether execution should fail if zero dependencies are resolved
     * @return               project's dependencies that passed all filters, resolved
     */
    @Requires({ dependency })
    @Ensures({ result || ( ! failIfNotFound ) })
    protected List<CopyDependency> resolveDependencies ( CopyDependency dependency, boolean failIfNotFound )
    {
        /**
         * We convert GAV dependency to Maven artifact to use it later
         */
        final mavenArtifact = dependency.gav ?
            dependency.with { toMavenArtifact( groupId, artifactId, version, '', type, classifier, optional ) }:
            null

        if ( dependency.single )
        {
            dependency.artifact = mojo.resolveArtifact( mavenArtifact, failIfNotFound )
            return [ dependency ]
        }

        /**
         * Iterating over all dependencies and selecting those passing the filters.
         */

        final artifacts = dependency.gav ?
            /**
             * For regular GAV dependency we collect its dependencies
             */
            collectDependencies( dependency, mavenArtifact, failIfNotFound ) :
            /**
             * Otherwise, we take all project's direct dependencies and collect their dependencies.
             * {@link org.apache.maven.project.MavenProject#getArtifacts} doesn't return transitive test dependencies
             * so we can't use it.
             */
            ( Collection<Artifact> ) mojo.project.dependencies.
            collect {
                Dependency mavenDependency ->
                final copyDependency = new CopyDependency( mavenDependency, dependency )
                collectDependencies( copyDependency, copyDependency.artifact, failIfNotFound )
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
                collect { Artifact artifact -> new CopyDependency( mojo.resolveArtifact( artifact, failIfNotFound )) }

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies [$dependency]: [${ dependencies.size() }] artifacts found" )
            if ( log.isDebugEnabled()) { log.debug( "Artifacts found: $dependencies" ) }

            assert ( dependencies || ( ! failIfNotFound )), "No dependencies resolved with [$dependency]"
            assert dependencies.every { it.artifact?.file?.file || it.optional || ( ! failIfNotFound ) }
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
     * @param dependency         dependency artifact originated from
     * @param artifact           artifact to collect dependencies of
     * @param failOnError        whether execution should fail if failed to collect dependencies
     * @param artifactsInProcess internal variable used by recursive iteration, shouldn't be used by caller!
     * @return                   dependencies collected (not resolved!)
     */
    @Requires({ dependency && artifact })
    @Ensures({ result })
    final Collection<Artifact> collectDependencies ( CopyDependency dependency,
                                                     Artifact       artifact,
                                                     boolean        failOnError,
                                                     Set<Artifact>  artifactsInProcess = [ artifact ].toSet())
    {
        try
        {
            final includeScope                  = split( dependency.includeScope )
            final excludeScope                  = split( dependency.excludeScope )
            final request                       = new CollectRequest( toAetherDependency( artifact ), mojo.remoteRepos )
            final previousSelector              = mojo.repoSession.dependencySelector
            mojo.repoSession.dependencySelector = new ScopeDependencySelector( includeScope , excludeScope )
            final rootNode                      = mojo.repoSystem.collectDependencies( mojo.repoSession, request ).root
            mojo.repoSession.dependencySelector = previousSelector

            if ( ! rootNode )
            {
                assert ( ! failOnError ), "Failed to collect [$artifact] dependencies"
                return Collections.emptyList()
            }

            final childArtifacts = rootNode.children.
            findAll {
                DependencyNode childNode ->
                (( ! childNode.dependency.optional ) || dependency.includeOptional )
            }.
            collect {
                DependencyNode childNode ->
                toMavenArtifact( childNode.dependency.artifact, childNode.dependency.scope )
            }

            if ( dependency.transitive )
            {
                /**
                 * Recursively iterating over node's children and collecting their transitive dependencies.
                 * The problem is the graph at this point contains only partial data, some of node's children
                 * may have dependencies not shown by the graph we have (I don't know why).
                 */
                childArtifacts.each {
                    Artifact childArtifact ->
                    if ( ! ( childArtifact in artifactsInProcess ))
                    {
                        collectDependencies( dependency, childArtifact, failOnError,
                                             (( Set<Artifact> )( artifactsInProcess << childArtifact )))
                    }
                }

                childArtifacts = artifactsInProcess
            }

            /**
             * Filtering out the final result before returning it since initial artifact was added to
             * recursion set without checking its scope.
             */
            childArtifacts.findAll { Artifact a -> scopeMatches( a.scope, includeScope, excludeScope ) }
        }
        catch ( e )
        {
            if ( failOnError ) { throw new RuntimeException( "Failed to collect [$artifact] dependencies", e ) }
            return Collections.emptyList()
        }
    }


    private org.sonatype.aether.graph.Dependency toAetherDependency( Artifact artifact )
    {
        new org.sonatype.aether.graph.Dependency( toAetherArtifact( artifact ), null )
    }


    /**
     * Retrieves filters defined by "filtering" dependency.
     *
     * @param dependency                  "filtering" dependency
     * @param useScopeTransitivityFilters whether scope and transitivity filters should be added
     * @return filters defined by "filtering" dependency
     */
    @Requires({ dependency && ( ! dependency.single ) && false }) // Checking GContracts
    @Ensures({  result     && false })                            // Checking GContracts
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
        final tempDir = file().tempDirectory()
        final f       = new File( tempDir, manifest.location )

        m.mainAttributes[ Attributes.Name.MANIFEST_VERSION ] = '1.0'
        manifest.entries.each{ String key, String value -> m.mainAttributes.putValue( key, value ) }

        file().mkdirs( f.parentFile )
        f.withOutputStream { m.write( it )}

        tempDir
    }
}
