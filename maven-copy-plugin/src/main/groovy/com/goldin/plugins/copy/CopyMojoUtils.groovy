package com.goldin.plugins.copy

import com.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.artifact.filter.collection.*
import static com.goldin.plugins.common.GMojoUtils.*


/**
 * {@link CopyMojo} helper class
 */
class CopyMojoUtils
{

    /**
     * Scans all dependencies that this project has (including transitive ones) and filters them with scoping
     * and transitivity filters provided in dependency specified.
     *
     * @param dependency filtering dependency
     * @return           project's dependencies that passed all filters
     */
    static List<CopyDependency> getFilteredDependencies( CopyDependency dependency )
    {
        /**
         * Iterating over all project's artifacts and selecting those passing all filters.
         * "test" = "compile" + "provided" + "runtime" + "test"
         * (http://maven.apache.org/pom.html#Dependencies)
         */
        List<ArtifactsFilter> filters      = getFilters( dependency )
        Set<Artifact>         allArtifacts = getArtifacts( 'test', 'system' )
        List<CopyDependency>  dependencies = allArtifacts.findAll { Artifact artifact -> filters.every{ it.isArtifactIncluded( artifact ) }}.
                                                          collect { Artifact artifact -> new CopyDependency( artifact ) }

        Log log = ThreadLocals.get( Log )

        log.info( "Resolving dependencies [$dependency]: [${ dependencies.size() }] artifacts found" )
        if ( log.isDebugEnabled()) { log.debug( "Artifacts found: $dependencies" ) }

        dependencies
    }


    private static List<ArtifactsFilter> getFilters( CopyDependency dependency )
    {
        List<ArtifactsFilter> filters = [
            new ProjectTransitivityFilter( ThreadLocals.get( MavenProject ).dependencyArtifacts,
                                           dependency.isExcludeTransitive()) ]

        if ( dependency.includeScope || dependency.excludeScope )
        {
            filters << new ScopeFilter( dependency.includeScope, dependency.excludeScope )
        }

        if ( dependency.includeGroupIds || dependency.excludeGroupIds )
        {
            filters << new GroupIdFilter( dependency.includeGroupIds, dependency.excludeGroupIds )
        }

        if ( dependency.includeArtifactIds || dependency.excludeArtifactIds )
        {
            filters << new ArtifactIdFilter( dependency.includeArtifactIds, dependency.excludeArtifactIds )
        }

        if ( dependency.includeClassifiers || dependency.excludeClassifiers )
        {
            filters << new ClassifierFilter( dependency.includeClassifiers, dependency.excludeClassifiers)
        }

        if ( dependency.includeTypes || dependency.excludeTypes )
        {
            filters << new TypeFilter( dependency.includeTypes, dependency.excludeTypes )
        }

        // First filter is transitivity filter, it is always added
        assert ( filters.size() > 1 ) : \
               "No filters found in <dependency>. Specify filters like <includeScope> or <includeGroupIds>."

        filters
    }
}
