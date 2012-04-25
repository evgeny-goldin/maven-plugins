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


    boolean optional        = false  // Whether this dependency is optional
    boolean includeOptional = false  // Whether this filtering dependency should include optional dependencies
    Boolean stripVersion             // Whether version number should be removed from file names
    Boolean excludeTransitive        // Whether transitive dependencies should be excluded

    String  includeScope
    String  excludeScope

    String  includeGroupIds
    String  excludeGroupIds

    String  includeArtifactIds
    String  excludeArtifactIds

    String  includeClassifiers
    String  excludeClassifiers

    String  includeTypes
    String  excludeTypes


    /**
     * Determines if current dependency has its GAV coordinates defined: groupId, artifactId, version.
     * @return true if current dependency has its GAV coordinates defined,
     *         false otherwise
     */
    boolean isGav ()
    {
        groupId && artifactId && version
    }


    /**
     * Determines if current dependency is "single" or "filtering".
     *
     * "Single" dependencies result in a single artifact being resolved specified with its GAV coordinates.
     * "Filtering" dependencies result in multiple artifacts being resolved through their include/exclude attributes.
     *
     * @return true if dependency is "single", false otherwise.
     */
    boolean isSingle()
    {
        gav &&
        (( excludeTransitive == null ) || ( excludeTransitive )) &&
        ( ! [ includeOptional,
              includeScope,       excludeScope,
              includeGroupIds,    excludeGroupIds,
              includeArtifactIds, excludeArtifactIds,
              includeClassifiers, excludeClassifiers,
              includeTypes,       excludeTypes ].any())
    }

    /**
     * Determines if current dependency is transitive or not.
     * When {@link #excludeTransitive} is not specified then the return value is false for GAV dependencies
     * and true for "filtering" dependencies. When {@link #excludeTransitive} is specified then its
     * value is reverted.
     *
     * @return true if dependency is transitive, false otherwise.
     */
    boolean isTransitive ()
    {
        ( excludeTransitive == null ) ? ( ! gav ) : ( ! excludeTransitive )
    }


    @Override
    String toString ()
    {
        final original = (( groupId || artifactId ) ? super.toString(): '' )
        final us       = [ "optional \"$optional\"",
                           "includeOptional \"$includeOptional\"",
                           excludeTransitive != null ? "exclude transitive \"$excludeTransitive\""  : '',
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


        original? "$original, $us" : us
    }
}
