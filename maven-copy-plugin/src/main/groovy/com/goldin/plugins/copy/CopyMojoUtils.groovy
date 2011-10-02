package com.goldin.plugins.copy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.artifact.filter.collection.*


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
    static List<CopyDependency> getDependencies ( CopyDependency dependency )
    {
        assert dependency
        def singleDependency = dependency.groupId && dependency.artifactId

        if ( singleDependency && dependency.getExcludeTransitive( singleDependency ))
        {
            // Simplest case: single <dependency> + <excludeTransitive> is undefined or "true" - dependency is returned
            return [ dependency ]
        }

        /**
         * Iterating over all dependencies and selecting those passing the filters.
         * "test" = "compile" + "provided" + "runtime" + "test" (http://maven.apache.org/pom.html#Dependencies)
         */
        List<ArtifactsFilter> filters   = getFilters( dependency, singleDependency )
        Collection<Artifact>  artifacts = singleDependency ?
            [ buildArtifact( dependency.groupId, dependency.artifactId, dependency.version, dependency.type ) ] :
            ThreadLocals.get( MavenProject ).artifacts

        List<CopyDependency>  dependencies = getArtifacts( artifacts, 'test', 'system' ).
                                             findAll { Artifact artifact -> filters.every{ it.isArtifactIncluded( artifact ) }}.
                                             collect { Artifact artifact -> new CopyDependency( artifact ) }

        Log log = ThreadLocals.get( Log )

        log.info( "Resolving dependencies [$dependency]: [${ dependencies.size() }] artifacts found" )
        if ( log.isDebugEnabled()) { log.debug( "Artifacts found: $dependencies" ) }

        assert dependencies, "Zero artifacts resolved from [$dependency]"
        dependencies
    }


    private static List<ArtifactsFilter> getFilters( CopyDependency dependency, boolean singleDependency )
    {
        /**
         * If we got here it's either because dependency is not single (filtered) or because *it is* single
         * with transitivity explicitly enabled
         */
        assert dependency
        assert ( ! singleDependency ) || ( dependency.excludeTransitive == false )

        List<ArtifactsFilter> filters = []
        def directDependencies        = singleDependency ? [ dependency ] as Set :
                                                           ThreadLocals.get( MavenProject ).dependencyArtifacts

        filters << new ProjectTransitivityFilter( directDependencies, dependency.getExcludeTransitive( singleDependency ))

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

        assert ( singleDependency || ( filters.size() > 1 )) : \
               'No filters found in <dependency>. Specify filters like <includeScope> or <includeGroupIds>.'

        filters
    }
}
