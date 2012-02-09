package com.goldin.plugins.ivy

import static com.goldin.plugins.common.GMojoUtils.*
import org.gcontracts.annotations.Requires
import org.gcontracts.annotations.Ensures
import org.apache.maven.artifact.Artifact
import org.apache.ivy.Ivy
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.module.descriptor.MDArtifact


/**
 * Contains various Ivy-related helper methods.
 */
class IvyHelper
{
    private final boolean verbose
    private final boolean failOnError

    IvyHelper ( boolean verbose, boolean failOnError )
    {
        this.verbose     = verbose
        this.failOnError = failOnError
    }


    /**
     * Resolve dependencies specified in Ivy file.
     *
     * @param ivy         configured {@link org.apache.ivy.Ivy} instance
     * @param ivyFile     ivy dependencies file
     * @param verbose     whether logging should be verbose
     * @param failOnError whether execution should fail if resolving artifacts doesn't succeed
     * @return Maven artifacts resolved
     */
    @Requires({ ivy && ivyFile })
    @Ensures({ result && result.every{ it.file.file } })
    @SuppressWarnings( 'GroovySetterCallCanBePropertyAccess' )
    List<Artifact> resolve ( Ivy ivy, URL ivyFile )
    {
        final options = new ResolveOptions()
        options.confs = [ 'default' ] as String[]
        options.log   = verbose ? 'default' : 'download-only'
        final report  = ivy.resolve( ivyFile, options )

        if ( verbose )
        {
            if ( report.downloadSize < 1 )
            {
                log.info( "[${ report.downloadSize }] bytes downloaded" )
            }
            else
            {
                log.info( "[${( long )( report.downloadSize / 1024 ) }] Kb downloaded in [${ ( long ) ( report.downloadTime / 1000 ) }] sec - " +
                          "[${ report.artifacts.size() }] artifact${ general().s( report.artifacts.size())} of " +
                          "[${ report.dependencies.size() }] dependenc${ report.dependencies.size() == 1 ? 'y' : 'ies' }"  )
            }
        }

        if ( report.unresolvedDependencies && failOnError )
        {
            throw new RuntimeException( "Failed to resolve [$ivyFile] dependencies: ${ report.unresolvedDependencies }" )
        }

        if ( report.allProblemMessages && failOnError )
        {
            throw new RuntimeException( "Errors found when resolving [$ivyFile] dependencies: ${ report.allProblemMessages }" )
        }

        report.allArtifactsReports.collect {
            ArtifactDownloadReport artifactReport ->
            assert artifactReport.artifact instanceof MDArtifact

            /**
             * Help me God, Ivy (http://db.tt/9Cf1X4bH).
             */
            Map    attributes = (( MDArtifact ) artifactReport.artifact ).md.metadataArtifact.id.moduleRevisionId.attributes
            String groupId    = attributes[ 'organisation' ]
            String artifactId = attributes[ 'module'       ]
            String version    = attributes[ 'revision'     ]
            String classifier = artifactReport.artifactOrigin.artifact.name // artifact name ("core/annotations" - http://goo.gl/se95h) plays as classifier
            File   localFile  = verify().file( artifactReport.localFile )

            if ( verbose )
            {
                log.info( "[${ ivyFile }] => \"$groupId:$artifactId:$classifier:$version\" (${ localFile.canonicalPath })" )
            }

            artifact( IvyMojo.IVY_PREFIX + groupId, artifactId, version, file().extension( localFile ), classifier, localFile )
        }
    }
}
