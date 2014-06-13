package com.github.goldin.plugins.common

import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

/**
 * -------------------------------------------------------------------------------------------------
 * DO NOT add any imports, keep all classes fully-qualified and explicit, there's much overlapping.
 * -------------------------------------------------------------------------------------------------
 */


/**
 * Utility methods for converting objects between Maven and Aether domains.
 */
final class ConversionUtils
{
    private ConversionUtils (){}

    @SuppressWarnings([ 'GroovyMethodParameterCount' ])
    @Requires({ groupId && artifactId && version })
    @Ensures ({ result })
    static org.apache.maven.artifact.Artifact toMavenArtifact (
            String  groupId,
            String  artifactId,
            String  version,
            String  scope,
            String  type,
            String  classifier,
            boolean optional,
            File    file = null )
    {
        final mavenArtifact = new org.apache.maven.artifact.DefaultArtifact(
                groupId,
                artifactId,
                org.apache.maven.artifact.versioning.VersionRange.createFromVersion( version ),
                scope ?: 'compile',
                type,
                classifier,
                new org.apache.maven.artifact.handler.DefaultArtifactHandler(),
                optional )

        if ( file ) { mavenArtifact.file = GMojoUtils.verifyBean().file( file ) }
        mavenArtifact
    }


    @Requires({ mavenDependency })
    @Ensures ({ result })
    static org.apache.maven.artifact.Artifact toMavenArtifact( org.apache.maven.model.Dependency mavenDependency )
    {
        mavenDependency.with { toMavenArtifact( groupId, artifactId, version, scope, type, classifier, optional ) }
    }


    @Requires({ aetherDependency })
    @Ensures ({ result })
    static org.apache.maven.artifact.Artifact toMavenArtifact ( org.eclipse.aether.graph.Dependency aetherDependency )
    {
        aetherDependency.artifact.with {
            toMavenArtifact( groupId, artifactId, version, aetherDependency.scope, extension, classifier, aetherDependency.optional, file )
        }
    }


    @Requires({ mavenArtifact })
    @Ensures ({ result })
    static org.eclipse.aether.artifact.Artifact toAetherArtifact ( org.apache.maven.artifact.Artifact mavenArtifact )
    {
        mavenArtifact.with {
            new org.eclipse.aether.artifact.DefaultArtifact(
                    groupId, artifactId, classifier, type, version, null, file )
        }
    }
}
