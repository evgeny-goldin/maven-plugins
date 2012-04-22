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
     * Scans all dependencies this project has (including transitive ones) and filters them with scoping
     * and transitivity filters provided in dependency specified. If dependency specified is not "filtering"
     * one then it is resolved normally.
     *
     * @param d              filtering dependency
     * @param failIfNotFound whether execution should fail if zero dependencies are resolved
     * @return               project's dependencies that passed all filters
     */
    @Requires({ d })
    @Ensures({ result != null })
    protected List<CopyDependency> resolveDependencies ( CopyDependency d, boolean failIfNotFound )
    {
        assert d
        final singleDependency = d.groupId && d.artifactId

        if ( singleDependency && d.getExcludeTransitive( singleDependency ))
        {
            // Simplest case: single <dependency> + <excludeTransitive> is undefined or "true" - dependency is returned
            d.artifact = mojoInstance.resolveArtifact( toMavenArtifact( d.groupId, d.artifactId, d.version, d.type, d.classifier ),
                                                       failIfNotFound )
            return [ d ]
        }

        /**
         * Iterating over all dependencies and selecting those passing the filters.
         */
        List<ArtifactsFilter> filters   = getFilters( d, singleDependency )
        Collection<Artifact>  artifacts = singleDependency ?
            mojoInstance.collectTransitiveDependencies( toMavenArtifact( d ), failIfNotFound ) :
            mojoInstance.project.artifacts

        try
        {
            List<CopyDependency>  dependencies =
                artifacts.
                findAll { Artifact artifact -> filters.every{ it.isArtifactIncluded( artifact ) }}.
                collect { Artifact artifact -> new CopyDependency( mojoInstance.resolveArtifact( artifact, failIfNotFound )) }

            Log log = ThreadLocals.get( Log )

            log.info( "Resolving dependencies [$d]: [${ dependencies.size() }] artifacts found" )
            if ( log.isDebugEnabled()) { log.debug( "Artifacts found: $dependencies" ) }

            assert ( dependencies || ( ! failIfNotFound )), "No dependencies resolved using [$d]"
            return dependencies
        }
        catch( e )
        {
            String errorMessage =
                'Failed to resolve and filter dependencies' +
                ( singleDependency ? " using ${ d.optional ? 'optional ' : '' }<dependency> [$d]" : '' )

            if ( d.optional || ( ! failIfNotFound ))
            {
                String exceptionMessage = e.toString()

                if ( e instanceof MultipleArtifactsNotFoundException )
                {
                    final missingArtifacts = (( MultipleArtifactsNotFoundException ) e ).missingArtifacts
                    exceptionMessage = "${ missingArtifacts.size() } missing dependenc${ missingArtifacts.size() == 1 ? 'y' : 'ies' } - $missingArtifacts"
                }

                log.warn( "$errorMessage: $exceptionMessage" )
                return []
            }

            throw new MojoExecutionException( errorMessage, e )
        }
    }

    /**
     * Converts {@link CopyDependency} instance to Maven {@link Artifact}.
     * @param d dependency to convert
     * @return  Maven {@link Artifact} instance
     */
    @Requires({ d })
    @Ensures({ result })
    private Artifact toMavenArtifact( CopyDependency d )
    {
        toMavenArtifact( d.groupId, d.artifactId, d.version, d.type, d.classifier )
    }


    private List<ArtifactsFilter> getFilters( CopyDependency dependency, boolean singleDependency )
    {
        /**
         * If we got here it's either because dependency is not single (filtered) or because *it is* single
         * with transitivity explicitly enabled (excludeTransitive is set to "false").
         */
        assert dependency
        // noinspection GroovyPointlessBoolean
        assert ( ! singleDependency ) || ( dependency.excludeTransitive == false )

        def c                         = { String s -> split( s ).join( ',' )} // Splits by "," and joins back to loose spaces
        List<ArtifactsFilter> filters = []
        def directDependencies        = singleDependency ?
            [ dependency ] as Set :                   // For single dependency it is it's own direct dependency
            mojoInstance.project.dependencyArtifacts  // Direct project dependencies for filtered dependencies

        filters << new ProjectTransitivityFilter( directDependencies, dependency.getExcludeTransitive( singleDependency ))

        if ( dependency.includeScope || dependency.excludeScope )
        {
            filters << new ScopeFilter( c ( dependency.includeScope ), c ( dependency.excludeScope ))
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

        assert ( singleDependency || ( filters.size() > 1 )) : \
               'No filters found in <dependency>. Specify filters like <includeScope> or <includeGroupIds>.'

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
