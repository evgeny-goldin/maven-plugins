package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.shared.artifact.filter.collection.*
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.deployment.DeployRequest
import org.sonatype.aether.graph.DependencyNode
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.graph.selector.ScopeDependencySelector
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.Attributes
import java.util.jar.Manifest


/**
 * {@link CopyMojo} helper class.
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
    List<String> updatePatterns( String directory, List<String> patterns, String encoding )
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
     * Scans project dependencies, resolves and filters them using dependencies provided.
     *
     * @param inputDependencies   dependencies to resolve, either "single" or "filtering" ones
     * @param eliminateDuplicates whether duplicate dependencies should be removed from result
     * @param parallelDownload    whether dependencies should be downloaded in parallel
     * @param verbose             whether resolving process should be logged
     * @param failIfNotFound      whether execution should fail if zero dependencies are resolved
     * @return                    project's dependencies that passed all filters, resolved (downloaded)
     */
    @Requires({ inputDependencies })
    @Ensures({ result || ( ! failIfNotFound ) })
    Collection<CopyDependency> resolveDependencies ( List<CopyDependency> inputDependencies,
                                                     boolean              eliminateDuplicates,
                                                     boolean              parallelDownload,
                                                     boolean              verbose,
                                                     boolean              failIfNotFound )
    {
        try
        {
            def dependencies = inputDependencies.collect {
                CopyDependency d -> collectDependencies( d, parallelDownload, verbose, failIfNotFound )
            }.flatten()

            if ( eliminateDuplicates ){ dependencies = removeDuplicates( dependencies )}

            each ( parallelDownload, dependencies ){
                CopyDependency d -> mojo.downloadArtifact( d.artifact, verbose, failIfNotFound )
            }

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies [$inputDependencies]: [${ dependencies.size() }] artifact${ generalBean().s( dependencies.size())} found" )
            if ( log.debugEnabled ) { log.debug( "Artifacts found: $dependencies" ) }

            assert ( dependencies || ( ! failIfNotFound ) || ( dependencies.every { it.optional } )), "No dependencies resolved with [$inputDependencies]"
            assert dependencies.every { it.artifact?.file?.file || it.optional || ( ! failIfNotFound ) }
            dependencies
        }
        catch( e )
        {
            String errorMessage = "Failed to resolve and filter dependencies with [$inputDependencies]"

            if ( inputDependencies.every { it.optional } || ( ! failIfNotFound ))
            {
                String exceptionMessage = e.toString()

                if ( e instanceof MultipleArtifactsNotFoundException )
                {
                    final missingArtifacts = (( MultipleArtifactsNotFoundException ) e ).missingArtifacts
                    exceptionMessage       = "${ missingArtifacts.size() } missing dependenc${ missingArtifacts.size() == 1 ? 'y' : 'ies' } - $missingArtifacts"
                }

                log.warn( "$errorMessage: $exceptionMessage" )
                return []
            }

            throw new MojoExecutionException( errorMessage, e )
        }
    }


    @Requires({ dependency })
    @Ensures ({ result.artifact && result.artifact.with { groupId && artifactId && version }})
    private Collection<CopyDependency> collectDependencies ( CopyDependency dependency,
                                                             boolean        parallelDownload,
                                                             boolean        verbose,
                                                             boolean        failIfNotFound )
    {
        final mavenArtifact = dependency.gav ?
            dependency.with { toMavenArtifact( groupId, artifactId, version, '', type, classifier, optional ) }:
            null

        if ( dependency.single )
        {
            return [ new CopyDependency( dependency, mavenArtifact ) ]
        }

        assert ( dependency.transitive || ( dependency.depth < 1 )), \
               "Dependency [$dependency] - depth is [$dependency.depth] while it is not transitive"

        final includeScopes = split( dependency.includeScope )
        final excludeScopes = split( dependency.excludeScope )
        final artifacts     = ( Collection<Artifact> ) dependency.gav ?

            /**
             * For regular GAV dependency we collect its dependencies
             */

            collectArtifactDependencies( mavenArtifact, includeScopes, excludeScopes,
                                         dependency.transitive, dependency.includeOptional,
                                         parallelDownload, verbose, failIfNotFound, dependency.depth ) :
            /**
             * Otherwise, we take project's direct dependencies matching the scopes and collect their dependencies.
             * {@link org.apache.maven.project.MavenProject#getArtifacts} doesn't return transitive test dependencies
             * so we can't use it.
             */

            mojo.project.dependencies.
            findAll { Dependency d -> scopeMatches( d.scope, includeScopes, excludeScopes ) }.
            collect { Dependency d -> toMavenArtifact( d ) }.
            collect { Artifact   a ->
                dependency.transitive ?
                    collectArtifactDependencies( a, includeScopes, excludeScopes,
                                                 dependency.transitive, dependency.includeOptional,
                                                 parallelDownload, verbose, failIfNotFound, dependency.depth ) :
                    a
            }.
            flatten().
            toSet()

        final filters = composeFilters( dependency )
        artifacts.findAll { Artifact a -> filters.every{ it.isArtifactIncluded( a ) }}.
                  collect { Artifact a -> new CopyDependency( dependency, a )}
    }


    /**
     * Removes duplicate versions of the same dependency by choosing the highest version.
     *
     * @param dependencies dependencies containing possible duplicates
     * @return new list of dependencies with duplicate eliminates removed
     */
    @Requires({ dependencies != null })
    @Ensures ({ result != null })
    private Collection<CopyDependency> removeDuplicates( Collection<CopyDependency> dependencies )
    {
        if ( dependencies.size() < 2 ) { return dependencies }

        /**
         * Mapping of "[groupId]::[artifactId]::[type]::[classifier]" to their duplicate dependencies
         */
        Map<String, List<CopyDependency>> mapping = dependencies.inject( [:].withDefault{ [] } ) {
            Map m, CopyDependency d ->
            assert d.groupId && d.artifactId
            m[ "[$d.groupId]::[$d.artifactId]::[${ d.type ?: '' }]::[${ d.classifier ?: '' }]".toString() ] << d
            m
        }

        /**
         * For every list of duplicates in the mapping, finding the maximal version
         * if there are more than one element in a list.
         */
        final result = mapping.values().collect {
            List<CopyDependency> duplicateDependencies ->
            assert               duplicateDependencies

            ( duplicateDependencies.size() < 2 ) ? duplicateDependencies.first() : duplicateDependencies.max {
                CopyDependency d1, CopyDependency d2 ->
                new DefaultArtifactVersion( d1.version ) <=> new DefaultArtifactVersion( d2.version )
            }
        }

        result
    }


    /**
     * Collects dependencies of the artifact specified.
     *
     * @param artifact            Artifact to collect dependencies of
     * @param includeScopes       dependencies scopes to include
     * @param excludeScopes       dependencies scopes to exclude
     * @param includeTransitive   whether dependencies should be traversed transitively
     * @param includeOptional     whether optional dependencies should be included
     * @param parallelDownload    whether dependencies should be collected in parallel
     * @param verbose             whether collecting process should be logged
     * @param failOnError         whether execution should fail if failed to collect dependencies
     * @param depth               depth of transitive dependencies to collect
     * @param currentDepth        current transitive dependencies depth
     * @param aggregator          aggregates recursive results
     * @return                    transitive dependencies collected (not resolved!)
     */
    @SuppressWarnings([ 'GroovyMethodParameterCount' ])
    @Requires({ artifact && ( includeScopes != null ) && ( excludeScopes != null ) && ( currentDepth >= 0 ) && ( aggregator != null ) })
    @Ensures ({ result != null })
    private final Collection<Artifact> collectArtifactDependencies (
            Artifact             artifact,
            List<String>         includeScopes,
            List<String>         excludeScopes,
            boolean              includeTransitive,
            boolean              includeOptional,
            boolean              parallelDownload,
            boolean              verbose,
            boolean              failOnError,
            int                  depth,
            int                  currentDepth = 0,
            Collection<Artifact> aggregator   = ( parallelDownload ? new ConcurrentLinkedQueue<Artifact>() :
                                                                     new HashSet<Artifact>()))
    {
        assert artifact.with { groupId && artifactId && version }
        assert (( depth < 0 ) || ( currentDepth <= depth )), "Required depth is [$depth], current depth is [$currentDepth]"
        assert ( includeTransitive || ( depth < 1 )), "[$artifact] - depth is [$depth] while request is not transitive"

        if ( scopeMatches( artifact.scope, includeScopes, excludeScopes )) { aggregator << artifact }

        if ( currentDepth == depth )
        {
            return aggregator
        }

        try
        {
            final request                  = new CollectRequest( new org.sonatype.aether.graph.Dependency( toAetherArtifact( artifact ), null ), mojo.remoteRepos )
            final repoSession              = mojo.repoSession
            final previousSelector         = repoSession.dependencySelector
            repoSession.dependencySelector = new ScopeDependencySelector( includeScopes, excludeScopes )

            if ( verbose )
            {
                mojo.log.info( "Collecting [$artifact] dependencies: include scopes $includeScopes, exclude scopes $excludeScopes, " +
                               "include transitive [$includeTransitive], include optional [$includeOptional]" )
            }

            final rootNode = mojo.repoSystem.collectDependencies( repoSession, request ).root

            repoSession.dependencySelector = previousSelector

            if ( ! rootNode )
            {
                assert ( ! failOnError ), "Failed to collect [$artifact] dependencies"
                return []
            }

            final childArtifacts = rootNode.children.
            findAll { DependencyNode childNode -> (( ! childNode.dependency.optional ) || includeOptional )}.
            collect { DependencyNode childNode -> toMavenArtifact( childNode.dependency.artifact, childNode.dependency.scope )}

            assert childArtifacts.every { scopeMatches( it.scope, includeScopes, excludeScopes )}

            if ( includeTransitive )
            {
                each( parallelDownload, childArtifacts ){
                    Artifact a ->

                    if ( ! ( a in aggregator ))
                    {
                        collectArtifactDependencies( a, includeScopes, excludeScopes, includeTransitive, includeOptional,
                                                     parallelDownload, verbose, failOnError, depth, currentDepth + 1, aggregator )
                    }
                }
            }
            else
            {
                aggregator.addAll( childArtifacts )
            }
        }
        catch ( e )
        {
            failOrWarn( failOnError, "Failed to collect [$artifact] dependencies", e )
        }

        aggregator
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
            buffer.append( "-${ artifact.version }".toString())
        }

        if ( artifact.classifier )
        {
            buffer.append( "-${ artifact.classifier }".toString())
        }

        buffer.append( ".${ artifact.type }".toString()).
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
