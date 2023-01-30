package com.github.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem
import org.gcontracts.annotations.Requires


/**
 * Presents a {@code <dependency>} to copy
 */
@SuppressWarnings( 'StatelessClass' )
class CopyDependency extends ArtifactItem
{
    CopyDependency() {}

    @SuppressWarnings([ 'GroovyUntypedAccess' ])
    @Requires({ dependency && artifact })
    CopyDependency( CopyDependency dependency, Artifact artifact )
    {
        super( artifact )

        this.optional       = dependency.optional
        this.stripVersion   = dependency.stripVersion
        this.stripTimestamp = dependency.stripTimestamp
        this.destFileName   = dependency.destFileName
    }


    boolean optional             = false  // Whether this dependency is optional
    boolean includeOptional      = false  // Whether this filtering dependency should include optional dependencies
    boolean applyWhileTraversing = false  // Whether [groupId, artifactId, classifier, type] filtering
                                          // should be applied while traversing the dependencies tree
    Boolean stripVersion                  // Whether version number should be removed from file names
    Boolean stripTimestamp                // Whether snapshot timestamp should be removed from file names
    Boolean useFinalName                  // Whether the file name is set to finalName from the artifact's POM
    Boolean excludeTransitive             // Whether transitive dependencies should be excluded
    int     depth           = -1          // Depth of transitive arguments

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
    boolean isGav () { groupId && artifactId && version }


    /**
     * Determines if current dependency is "single" or "filtering".
     *
     * "Single" dependencies result in a single artifact being resolved specified with its GAV coordinates.
     * "Filtering" dependencies result in multiple artifacts being resolved through their include/exclude attributes.
     *
     * @return true if dependency is "single", false otherwise.
     */
    boolean isSingle ()
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
     * When {@link #excludeTransitive} is not specified then the return value is false for single dependencies
     * and true for "filtering" dependencies. When {@link #excludeTransitive} is specified then its
     * value is reverted.
     *
     * @return true if dependency is transitive, false otherwise.
     */
    boolean isTransitive ()
    {
        ( excludeTransitive != null ) ? ( ! excludeTransitive ) : ( ! single )
    }


    @Override
    String toString ()
    {
        final original = (( groupId || artifactId ) ? super.toString(): '' )
        final ours     = [ "includeOptional \"$includeOptional\"",
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
                           excludeTypes              ? "excludeTypes \"$excludeTypes\""             : '',
                           "optional \"$optional\""
                         ].grep().join( ', ' )


        original? "$original, $ours" : ours
    }
}
