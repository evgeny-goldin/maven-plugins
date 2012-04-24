package com.github.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem

 /**
 * Presents a {@code <dependency>} to copy
 */
@SuppressWarnings( 'StatelessClass' )
class CopyDependency extends ArtifactItem
{
    CopyDependency() {}
    CopyDependency( Artifact artifact ) { super( artifact )  }


    boolean optional = false
    Boolean stripVersion
    Boolean excludeTransitive
    boolean getExcludeTransitive ( boolean singleDependency )
    {
        /**
         * For single dependency resolution default "excludeTransitive" is true: single dependency is resolved without transitive dependencies by default
         * For filtered dependencies default "excludeTransitive" is false: filtered dependencies are resolved with transitive dependencies by default
         */
        ( excludeTransitive == null ) ? singleDependency : excludeTransitive
    }


    String includeScope
    String excludeScope

    String  includeGroupIds
    String  excludeGroupIds

    String  includeArtifactIds
    String  excludeArtifactIds

    String  includeClassifiers
    String  excludeClassifiers

    String  includeTypes
    String  excludeTypes


    @Override
    String toString ()
    {
        def s = [ excludeTransitive != null ? "exclude transitive \"$excludeTransitive\""  : '',
                  includeScope              ? "includeScope \"$includeScope\""             : '',
                  excludeScope              ? "excludeScope \"$excludeScope\""             : '',
                  includeGroupIds           ? "includeGroupIds \"$includeGroupIds\""       : '',
                  excludeGroupIds           ? "excludeGroupIds \"$excludeGroupIds\""       : '',
                  includeArtifactIds        ? "includeArtifactIds \"$includeArtifactIds\"" : '',
                  excludeArtifactIds        ? "excludeArtifactIds \"$excludeArtifactIds\"" : '',
                  includeClassifiers        ? "includeClassifiers \"$includeClassifiers\"" : '',
                  excludeClassifiers        ? "excludeClassifiers \"$excludeClassifiers\"" : '',
                  includeTypes              ? "includeTypes \"$includeTypes\""             : '',
                  excludeTypes              ? "excludeTypes \"$excludeTypes\""             : ''
                ].grep().join( ', ' )

        (( groupId && artifactId ) ? super.toString() + ' ' : '' ) + ( s ?: '' )
    }
}
