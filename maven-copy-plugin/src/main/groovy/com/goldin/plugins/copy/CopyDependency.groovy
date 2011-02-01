package com.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem

 /**
 * Presents a {@code <dependency>} to copy
 */
class CopyDependency extends ArtifactItem
{
    CopyDependency() {}
    CopyDependency( Artifact artifact ) { super( artifact )  }


    boolean optional          = false
    boolean excludeTransitive = false

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


    @Override
    public String toString ()
    {
        def allToString =
        [
            includeScope       ? "includeScope \"$includeScope\""             : '',
            excludeScope       ? "excludeScope \"$excludeScope\""             : '',
            includeGroupIds    ? "includeGroupIds \"$includeGroupIds\""       : '',
            excludeGroupIds    ? "excludeGroupIds \"$excludeGroupIds\""       : '',
            includeArtifactIds ? "includeArtifactIds \"$includeArtifactIds\"" : '',
            excludeArtifactIds ? "excludeArtifactIds \"$excludeArtifactIds\"" : '',
            includeClassifiers ? "includeClassifiers \"$includeClassifiers\"" : '',
            excludeClassifiers ? "excludeClassifiers \"$excludeClassifiers\"" : '',
            includeTypes       ? "includeTypes \"$includeTypes\""             : '',
            excludeTypes       ? "excludeTypes \"$excludeTypes\""             : ''
        ]

        allToString.any() ? "Exclude transitive \"$excludeTransitive\", ${ allToString.findAll{ it }.join( ', ' ) }" :
                            super.toString()
    }
}
