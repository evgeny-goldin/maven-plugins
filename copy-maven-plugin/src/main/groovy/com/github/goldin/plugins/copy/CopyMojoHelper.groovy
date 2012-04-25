package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.shared.artifact.filter.collection.*

import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

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
    private final BaseGroovyMojo mojoInstance

    @Requires({ mojoInstance })
    CopyMojoHelper ( BaseGroovyMojo mojoInstance )
    {
        this.mojoInstance = mojoInstance
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
     * @param d              dependency to resolve, either "single" or "filtering" one
     * @param failIfNotFound whether execution should fail if zero dependencies are resolved
     * @return               project's dependencies that passed all filters, resolved
     */
    @Requires({ d })
    @Ensures({ result || ( ! failIfNotFound ) })
    protected List<CopyDependency> resolveDependencies ( CopyDependency d, boolean failIfNotFound )
    {
        /**
         * When GAV coordinates are specified we convert the dependency to Maven artifact
         */
        final mavenArtifact = d.gav ? toMavenArtifact( d.groupId, d.artifactId, d.version, '', d.type, d.classifier ) :
                                      null
        if ( d.single )
        {
            d.artifact = mojoInstance.resolveArtifact( mavenArtifact, failIfNotFound )
            return [ d ]
        }

        /**
         * Iterating over all dependencies and selecting those passing the filters.
         */

        Collection<Artifact> artifacts = d.gav ?
            // For GAV dependency we collect its dependencies
            mojoInstance.collectDependencies( mavenArtifact, d.includeScope, d.excludeScope, d.transitive, d.includeOptional, failIfNotFound ) :
            // Otherwise, we take all project's transitive dependencies
            mojoInstance.project.artifacts.findAll { ( ! it.optional ) || d.includeOptional }

        try
        {
            /**
             * When GAV coordinates appear, scope and transitivity were applied already by {@link BaseGroovyMojo#collectDependencies} call above.
             */
            final                filters      = getFilters( d, ( ! d.gav ))
            List<CopyDependency> dependencies =
                artifacts.
                findAll { Artifact artifact -> filters.every{ it.isArtifactIncluded( artifact ) }}.
                collect { Artifact artifact -> new CopyDependency( mojoInstance.resolveArtifact( artifact, failIfNotFound )) }

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies [$d]: [${ dependencies.size() }] artifacts found" )
            if ( log.isDebugEnabled()) { log.debug( "Artifacts found: $dependencies" ) }

            assert ( dependencies || ( ! failIfNotFound )), "No dependencies resolved using [$d]"
            assert dependencies.every { it.artifact.file.file }
            dependencies
        }
        catch( e )
        {
            String errorMessage = "Failed to resolve and filter dependencies using [$d]"

            if ( d.optional || ( ! failIfNotFound ))
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
     * Retrieves filters defined by "filtering" dependency.
     *
     * @param dependency                  "filtering" dependency
     * @param useScopeTransitivityFilters whether scope and transitivity filters should be added
     * @return filters defined by "filtering" dependency
     */
    @Requires({ dependency && ( ! dependency.single ) && false }) // Checking GContracts
    @Ensures({  result     && false })                            // Checking GContracts
    private List<ArtifactsFilter> getFilters( CopyDependency dependency, boolean useScopeTransitivityFilters )
    {
        assert dependency && ( ! dependency.single )

        def c                         = { String s -> split( s ).join( ',' )} // Splits by "," and joins back to loose spaces
        List<ArtifactsFilter> filters = []
        final directDependencies      = dependency.gav ?
            [ dependency ] as Set :                   // For regular  dependency it is it's own direct dependency
            mojoInstance.project.dependencyArtifacts  // For filtered dependencies it is direct project dependencies

        if ( useScopeTransitivityFilters )
        {
            filters << new ProjectTransitivityFilter( directDependencies, ( ! dependency.transitive ))

            if ( dependency.includeScope || dependency.excludeScope )
            {
                filters << new ScopeFilter( split( dependency.includeScope ), split( dependency.excludeScope ))
            }
        }

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

        assert ( filters || ( ! useScopeTransitivityFilters )) : \
               "No filters found in <dependency> [$dependency]. Specify filters like <includeScope> or <includeGroupIds>."
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


    /**
     * Creates artifact file name, identically to
     * {@link org.apache.maven.plugin.dependency.utils.DependencyUtil#getFormattedFileName}.
     *
     * @param artifact      artifact to create the file name for
     * @param removeVersion whether version should be removed from the file name
     * @return artifact file name
     */
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
}
