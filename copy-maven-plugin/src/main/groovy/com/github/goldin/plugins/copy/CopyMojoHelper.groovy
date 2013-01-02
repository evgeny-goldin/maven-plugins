package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.ConversionUtils.*
import static com.github.goldin.plugins.common.GMojoUtils.*
import org.sonatype.aether.resolution.ArtifactDescriptorRequest
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
import org.sonatype.aether.deployment.DeployRequest
import org.sonatype.aether.repository.RemoteRepository
import java.util.jar.Attributes
import java.util.jar.Manifest


/**
 * {@link CopyMojo} helper class.
 */
@SuppressWarnings([ 'FinalClassWithProtectedMember', 'GroovyAccessibility' ])
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
    @Ensures ({ result != null })
    Collection<CopyDependency> resolveDependencies ( List<CopyDependency> inputDependencies,
                                                     boolean              eliminateDuplicates,
                                                     boolean              parallelDownload,
                                                     boolean              verbose,
                                                     boolean              failIfNotFound )
    {
        try
        {
            Collection<CopyDependency>  result = inputDependencies.collect { collectDependencies( it, failIfNotFound )}.flatten()
            if ( eliminateDuplicates ){ result = removeDuplicates( result )}

            each ( parallelDownload, result ){ CopyDependency d -> mojo.downloadArtifact( d.artifact, verbose, failIfNotFound )}

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies $inputDependencies: [${ result.size() }] artifact${ generalBean().s( result.size())} found" )
            if ( log.debugEnabled ) { log.debug( "Artifacts found: $result" ) }

            assert ( result || ( ! failIfNotFound ) || ( inputDependencies.every { it.optional } )), "No dependencies resolved with $inputDependencies"
            assert result.every { CopyDependency d -> d.artifact?.file?.file || d.optional || ( ! failIfNotFound ) }
            result
        }
        catch( e )
        {
            String errorMessage = "Failed to resolve and filter dependencies with $inputDependencies"

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
    @Ensures ({ result != null })
    private Collection<CopyDependency> collectDependencies ( CopyDependency dependency, boolean failIfNotFound )
    {
        final isTransitive  = dependency.transitive
        final depth         = dependency.depth
        final mavenArtifact = dependency.gav ? dependency.with { toMavenArtifact( groupId, artifactId, version, '', type, classifier, optional ) }:
                                               null

        assert ( isTransitive || ( depth < 1 )), \
               "Depth is [$depth] for dependency [$dependency] that is not transitive"

        if ( dependency.single ) { return [ new CopyDependency( dependency, mavenArtifact ) ]}

        final scopeFilter      = new ScopeArtifactsFilter( split( dependency.includeScope ), split( dependency.excludeScope ))
        final dependencyFilter = new AndArtifactsFilter  ( composeDependencyFilters( dependency ))
        final artifacts        = dependency.gav ?

            collectArtifactDependencies( mavenArtifact, scopeFilter, dependencyFilter, dependency.applyWhileTraversing,
                                         false, dependency.includeOptional, failIfNotFound,
                                         isTransitive ? Math.max( -1, depth ) : ( depth == 0 ? 0 : 1 ))
            :

            mojo.project.dependencies.
            collect { Dependency d -> toMavenArtifact( d ) }.
            collect { Artifact   a -> collectArtifactDependencies( a, scopeFilter, dependencyFilter, dependency.applyWhileTraversing,
                                                                   true, dependency.includeOptional, failIfNotFound,
                                                                   isTransitive ? Math.max( -1, depth ) : 0 )
            }.
            flatten()

        assert artifacts.every { isArtifactIncluded( it, scopeFilter, dependencyFilter ) }
        artifacts.toSet().collect { new CopyDependency( dependency, it )}
    }


    /**
     * Determines if artifact specified is selected by all selectors provided.
     */
    @Requires({ artifact && ( filters != null ) })
    private boolean isArtifactIncluded ( Artifact artifact, ArtifactsFilter ... filters )
    {
        filters.every { it.isArtifactIncluded( artifact ) }
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
     * @param artifact            {@link Artifact} to collect dependencies of
     * @param scopeFilter         filter based on on include/exclude scopes
     * @param dependencyFilter    filter based on dependency filters (groupId, artifactId, classifier, type)
     * @param projectDependencies whether request initially originated from traversing project's dependencies (vs. traversing single artifact dependencies)
     * @param includeOptional     whether optional dependencies should be included
     * @param failOnError         whether execution should fail if failed to collect dependencies
     * @param depth               depth of transitive dependencies to collect
     * @param currentDepth        current transitive dependencies depth
     * @param resultAggregator    aggregates results
     * @param visitedAggregator   aggregates visited artifacts
     * @return                    artifact's dependencies collected (but not resolved!)
     */
    @SuppressWarnings([ 'GroovyMethodParameterCount' ])
    @Requires({ artifact                      &&
                scopeFilter                   &&
                dependencyFilter              &&
                ( currentDepth      >= 0    ) &&
                ( resultAggregator  != null ) &&
                ( visitedAggregator != null ) })
    @Ensures ({ result != null })
    private final Collection<Artifact> collectArtifactDependencies ( Artifact        artifact,
                                                                     ArtifactsFilter scopeFilter,
                                                                     ArtifactsFilter dependencyFilter,
                                                                     boolean         applyWhileTraversing,
                                                                     boolean         projectDependencies,
                                                                     boolean         includeOptional,
                                                                     boolean         failOnError,
                                                                     int             depth,
                                                                     int             currentDepth      = 0,
                                                                     Set<Artifact>   resultAggregator  = new HashSet<Artifact>(),
                                                                     Set<Artifact>   visitedAggregator = new HashSet<Artifact>())
    {
        assert artifact.groupId && artifact.artifactId && artifact.version
        assert ( ! (( artifact in resultAggregator ) || ( artifact in visitedAggregator )))
        assert (( depth < 0 ) || ( currentDepth <= depth )), "Required depth is [$depth], current depth is [$currentDepth]"

        if ( projectDependencies  && ( ! isArtifactIncluded( artifact, scopeFilter      ))){ return resultAggregator } // Excluded by scope filtering
        if ( applyWhileTraversing && ( ! isArtifactIncluded( artifact, dependencyFilter ))){ return resultAggregator } // Excluded by dependency filtering
        if ( artifact.optional    && ( ! includeOptional ))                                { return resultAggregator } // Excluded by being optional

        visitedAggregator << artifact
        if ( isArtifactIncluded( artifact, scopeFilter, dependencyFilter )){ resultAggregator << artifact }
        if ( currentDepth == depth ){ return resultAggregator }

        try
        {
            final request = new ArtifactDescriptorRequest( toAetherArtifact( artifact ), mojo.remoteRepos, null )

            mojo.repoSystem.readArtifactDescriptor( mojo.repoSession, request ).
            dependencies.
            collect { toMavenArtifact( it )}.
            each    {
                Artifact childArtifact ->

                if ( ! ( childArtifact in visitedAggregator )) // Go recursive for newly met artifacts only
                {
                    collectArtifactDependencies( childArtifact, scopeFilter, dependencyFilter, applyWhileTraversing,
                                                 true, includeOptional, failOnError,
                                                 depth,
                                                 currentDepth + 1, resultAggregator, visitedAggregator )
                }
            }
        }
        catch ( e )
        {
            failOrWarn( failOnError, "Failed to collect [$artifact] dependencies", e )
        }

        resultAggregator
    }


    /**
     * Composes {@link ArtifactsFilter} instances based on a "filtering" dependency.
     *
     * @param dependency  "filtering" dependency
     * @return filters defined by the "filtering" dependency specified
     */
    @Requires({ dependency && ( ! dependency.single ) })
    @Ensures ({ result != null })
    private List<ArtifactsFilter> composeDependencyFilters ( CopyDependency dependency )
    {
        List<ArtifactsFilter> filters = []
        final clean                   = { String s -> split( s ).join( ',' )} // Splits by "," and joins back to loose spaces
        final addFilter               = {
            String includePattern, String excludePattern, Class<? extends ArtifactsFilter> filterClass ->

            if ( includePattern || excludePattern )
            {
                filters << filterClass.newInstance( clean ( includePattern ), clean ( excludePattern ))
            }
        }

        dependency.with {
            addFilter( includeGroupIds,    excludeGroupIds,    GroupIdFilter    )
            addFilter( includeArtifactIds, excludeArtifactIds, ArtifactIdFilter )
            addFilter( includeClassifiers, excludeClassifiers, ClassifierFilter )
            addFilter( includeTypes,       excludeTypes,       TypeFilter       )
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
        request.artifacts  = [ toAetherArtifact( toMavenArtifact(
            groupId, artifactId, version, '', fileBean().extension( f ), classifier, false, f )) ]

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
