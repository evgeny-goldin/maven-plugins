package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.ConversionUtils.*
import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.Replace
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.artifact.filter.collection.*
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.codehaus.plexus.util.FileUtils
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.eclipse.aether.deployment.DeployRequest
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.apache.maven.shared.filtering.DefaultMavenFileFilter.Wrapper
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
        Collection<CopyDependency> result

        try
        {
            result = inputDependencies.collect { collectDependencies( it, failIfNotFound )}.flatten()
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
                return result
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
        final mavenArtifact = dependency.gav ? ( Artifact ) dependency.with { toMavenArtifact( groupId, artifactId, version, '', type, classifier, optional ) }:
                                               null

        assert ( isTransitive || ( depth < 1 )), \
               "Depth is [$depth] for dependency [$dependency] that is not transitive"

        if ( dependency.single ) { return [ new CopyDependency( dependency, mavenArtifact ) ]}

        final scopeFilter      = new ScopeArtifactsFilter( split( dependency.includeScope ), split( dependency.excludeScope ))
        final dependencyFilter = new AndArtifactsFilter  ( composeDependencyFilters( dependency ))
        final artifacts        = dependency.gav ?

            collectArtifactDependencies( mavenArtifact, scopeFilter, dependencyFilter,
                                         false, dependency.applyWhileTraversing, dependency.includeOptional, failIfNotFound,
                                         isTransitive ? Math.max( -1, depth ) : ( depth == 0 ? 0 : 1 ))
            :

            mojo.project.dependencies.
            collect { Dependency d -> toMavenArtifact( d ) }.
            collect { Artifact   a -> collectArtifactDependencies( a, scopeFilter, dependencyFilter,
                                                                   true, dependency.applyWhileTraversing, dependency.includeOptional, failIfNotFound,
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
     * @param artifact                {@link Artifact} to collect dependencies of
     * @param scopeFilter             filter based on on include/exclude scopes
     * @param dependencyFilter        filter based on dependency filters (groupId, artifactId, classifier, type)
     * @param respectScopeFilter      whether scope filter should be respected when traversing the tree
     * @param respectDependencyFilter whether dependency filter should be respected when traversing the tree
     * @param includeOptional         whether optional dependencies should be included
     * @param failOnError             whether execution should fail if failed to collect dependencies
     * @param depth                   depth of transitive dependencies to collect
     * @param currentDepth            current transitive dependencies depth
     * @param resultAggregator        aggregates results
     * @param visitedAggregator       aggregates visited artifacts
     * @return                        artifact's dependencies collected (but not resolved!)
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
                                                                     boolean         respectScopeFilter,
                                                                     boolean         respectDependencyFilter,
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

        boolean stopRecursion =
            ( respectScopeFilter      && ( ! isArtifactIncluded( artifact, scopeFilter      ))) ||  // Excluded by scope filtering
            ( respectDependencyFilter && ( ! isArtifactIncluded( artifact, dependencyFilter ))) ||  // Excluded by dependency filtering
            ( artifact.optional       && ( ! includeOptional ))                                     // Excluded by being optional

        if ( stopRecursion ) { return resultAggregator }

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
                    collectArtifactDependencies( childArtifact, scopeFilter, dependencyFilter,
                                                 true, respectDependencyFilter, includeOptional, failOnError,
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
     * @param artifact       artifact to create the file name for
     * @param stripVersion   whether version should be removed from the file name
     * @param stripTimestamp whether timestamp should be removed from snapshot file name
     * @return artifact file name
     */
    @Requires({ artifact && artifact.artifactId && artifact.version && artifact.type })
    String artifactFileName( Artifact artifact, boolean stripVersion, boolean stripTimestamp )
    {
        StringBuilder buffer = new StringBuilder( artifact.artifactId )

        if ( ! stripVersion )
        {
            buffer.append( "-${ stripTimestamp ? stripTimestampFromVersion( artifact.version ) : artifact.version }".toString())
        }

        if ( artifact.classifier )
        {
            buffer.append( "-${ artifact.classifier }".toString())
        }

        buffer.append( ".${ artifact.type }".toString()).toString()
    }


    @Requires({ timestampedSnapshot })
    @Ensures ({ result })
    String stripTimestampFromVersion ( String timestampedSnapshot )
    {
        // http://repo/evgenyg/tests-local/com/github/goldin/about-maven-plugin/0.3-SNAPSHOT/about-maven-plugin-0.3-20130114.213852-1.jar
        timestampedSnapshot.replaceFirst( ~/-\d{8}\.\d{6}-\d+/, '-SNAPSHOT' )
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


    /**
     *
     * Copies source file to destination applying replacements and filtering.
     *
     * @param sourceFile               source file to copy
     * @param destinationFile          destination file to copy the source to
     * @param skipIdentical            whether identical files should be skipped (not copied)
     * @param skipIdenticalUseChecksum whether identical files should be identified using checksum (true) or their timestamp (false)
     * @param replaces                 replacements to make
     * @param filtering                whether Maven filtering should be performed
     * @param encoding                 Filtering/replacement encoding
     * @param fileFilter               {@link MavenFileFilter} instance, allowed to be null if filter is false
     * @param verbose                  whether information is written to log with "INFO" level
     * @param move                     whether file should be moved and not copied
     * @param filterWithDollarOnly     whether only ${ .. } expressions should be recognized as delimiters when files are filtered
     *
     * @return destinationFile if file was copied,
     *         null            if file was skipped (identical)
     */
    @SuppressWarnings([ 'MethodSize', 'AbcComplexity', 'CyclomaticComplexity', 'GroovyMethodParameterCount' ])
    @Requires({ sourceFile.file && destinationFile && ( ! netBean().isNet( destinationFile.path )) && ( replaces != null ) && encoding })
    File copyFile ( final File            sourceFile,
                    final File            destinationFile,
                    final boolean         skipIdentical,
                    final boolean         skipIdenticalUseChecksum,
                    final Replace[]       replaces,
                    final boolean         filtering,
                    final String          encoding,
                    final MavenFileFilter fileFilter,
                    final boolean         verbose,
                    final boolean         move,
                    final boolean         filterWithDollarOnly )
    {
        File             fromFile           = sourceFile
        boolean          operationPerformed = false
        boolean          operationSkipped   = false
        Closure<Boolean> samePath           = { fromFile.canonicalPath == destinationFile.canonicalPath }

        assert ! ( move && samePath()), \
               "It is not possible to <move> [$fromFile] into itself - <move> means source file is deleted"

        /**
         * Deleting destination file if possible
         */
        if ( ! ( skipIdentical || samePath())) { fileBean().mkdirs( fileBean().delete( destinationFile ).parentFile )}

        try
        {
            if ( filtering && (( ! filterWithDollarOnly ) || fromFile.getText( encoding ).contains( '${' )))
            {
                List<FileUtils.FilterWrapper> wrappers =
                    fileFilter.getDefaultFilterWrappers( ThreadLocals.get( MavenProject ), null, false,
                                                         ThreadLocals.get( MavenSession ), new MavenResourcesExecution())
                if ( filterWithDollarOnly )
                {
                    wrappers.each { Wrapper wrapper -> wrapper.delimiters = [ '${*}' ] as Set }
                }
                else if ( fileBean().extension( fromFile ).toLowerCase() == 'bat' )
                {
                    log.warn( "[$fromFile] - filtering *.bat files without <filterWithDollarOnly> may not work correctly due to '@' character, " +
                              'see http://evgeny-goldin.org/youtrack/issue/pl-233.' )
                }

                File tempFile = null
                if ( samePath())
                {
                    tempFile = fileBean().tempFile()
                    fileBean().copy( fromFile, tempFile.parentFile, tempFile.name )
                    if ( verbose ) { log.info( "[$fromFile] copied to [$tempFile] (to filter it into itself)" ) }

                    fromFile = tempFile
                }

                assert ! samePath()
                fileBean().mkdirs( destinationFile.parentFile )
                fileFilter.copyFile( fromFile, destinationFile, true, wrappers, encoding, true )
                // noinspection JavaStylePropertiesInvocation, GroovySetterCallCanBePropertyAccess
                assert destinationFile.setLastModified( System.currentTimeMillis())
                fileBean().delete(( tempFile ? [ tempFile ] : [] ) as File[] )

                if ( verbose ) { log.info( "[$fromFile] filtered to [$destinationFile]" ) }

                /**
                 * - Destination file created becomes input file for the following operations
                 * - If no replacements should be made - we're done
                 */
                fromFile           = destinationFile
                operationPerformed = ( replaces.size() < 1 )
            }

            if ( replaces )
            {
                final content = ( String ) replaces.inject( fromFile.getText( encoding )){ String s, Replace r -> r.replace( s, fromFile ) }
                write( destinationFile, content, encoding )
                if ( verbose ) { log.info( "[$fromFile] content written to [$destinationFile], " +
                                           "[${ replaces.size()}] replace${ generalBean().s( replaces.size()) } made" )}
                operationPerformed = true
            }

            final boolean identical =
                ( skipIdentical && destinationFile.file && ( ! operationPerformed ) &&
                  identicalFiles( fromFile, destinationFile, skipIdenticalUseChecksum ))

            if ( identical )
            {
                if ( verbose ) { log.info( "[$fromFile] skipped - content is identical to destination [$destinationFile]" ) }
                operationPerformed = true
                operationSkipped   = true
            }

            if ( ! operationPerformed )
            {
                if ( samePath())
                {
                    log.warn( "[$fromFile] skipped - path is identical to destination [$destinationFile]" )
                    operationPerformed = true
                    operationSkipped   = true
                }
                else if ( move )
                {
                    operationPerformed = fromFile.renameTo( destinationFile )
                    if ( verbose && operationPerformed ) { log.info( "[$fromFile] renamed to [$destinationFile]" )}
                }

                if ( ! operationPerformed )
                {
                    fileBean().copy( fromFile, destinationFile.parentFile, destinationFile.name )
                    if ( verbose ) { log.info( "[$fromFile] ${ move ? 'moved' : 'copied' } to [$destinationFile]" )}
                }
            }

            /**
             * If it's a "move" operation and file content was filtered/replaced or renameTo() call
             * doesn't succeed - source file is deleted
             */
            if ( move && sourceFile.file && ( sourceFile.canonicalPath != destinationFile.canonicalPath )) { fileBean().delete( sourceFile ) }
            ( operationSkipped ? null : verifyBean().file( destinationFile ))
        }
        catch ( e )
        {
            throw new MojoExecutionException( "Failed to copy [$sourceFile] to [$destinationFile]", e )
        }
    }


    @Requires({ fromFile.file && destinationFile.file })
    private boolean identicalFiles ( File fromFile, File destinationFile, boolean useChecksum )
    {
        if ( fromFile.length() != destinationFile.length()) { return false }

        if ( useChecksum )
        {
            fileBean().checksum( fromFile ) == fileBean().checksum( destinationFile )
        }
        else
        {
            fromFile.lastModified() == destinationFile.lastModified()
        }
    }
}
