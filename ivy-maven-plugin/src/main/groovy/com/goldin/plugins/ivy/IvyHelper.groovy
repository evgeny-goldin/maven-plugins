package com.goldin.plugins.ivy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.gcommons.GCommons
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultIncludeRule
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.MDArtifact
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter
import org.apache.maven.artifact.Artifact
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.apache.ivy.core.settings.IvySettings


/**
 * Contains various Ivy-related helper methods.
 */
class IvyHelper
{
    static final String SEPARATOR  = '\n * '

    final Ivy     ivy
    final URL     ivyconfUrl
    final boolean verbose
    final boolean failOnError


    /**
     * Creates new instance of this helper class.
     *
     * @param ivyconfUrl  Ivy settings file {@link URL}.
     * @param verbose     whether logging should be verbose.
     * @param failOnError whether execution should fail when resolving Ivy artifacts fails.
     */
    @Requires({ ivyconfUrl })
    IvyHelper ( URL ivyconfUrl, boolean verbose, boolean failOnError )
    {
        IvySettings settings = new IvySettings()
        settings.load( ivyconfUrl )

        this.ivy         = Ivy.newInstance( settings )
        this.ivyconfUrl  = ivyconfUrl
        this.verbose     = verbose
        this.failOnError = failOnError
    }


    /**
     * Resolves dependency specified.
     *
     * @param groupId    dependency {@code <groupId>}
     * @param artifactId dependency {@code <artifactId>}
     * @param version    dependency {@code <version>}
     * @param classifier dependency {@code <classifier>}
     * @param type       dependency {@code <type>}
     *
     * @return Maven artifact resolved or null if resolution fails.
     * @throws RuntimeException if artifact resolution fails and {@link #failOnError} is {@code true}.
     */
    @Requires({ groupId && artifactId && version && type })
    Artifact resolve ( String groupId, String artifactId, String version, String classifier, String type )
    {
        final ivyfile = File.createTempFile( 'ivy', '.xml' )
        ivyfile.deleteOnExit();

        try
        {
            final md      = DefaultModuleDescriptor.newDefaultInstance( ModuleRevisionId.newInstance( groupId, artifactId + '-caller', 'working' ))
            final module  = ModuleRevisionId.newInstance( groupId, artifactId, version )
            final dd      = new DefaultDependencyDescriptor( md, module, false, false, true )

            if ( classifier )
            {
                dd.addIncludeRule( '', new DefaultIncludeRule( new ArtifactId( module.moduleId, classifier, type, type ),
                                                               new ExactPatternMatcher(), [:] ))
            }

            md.addDependency( dd );
            XmlModuleDescriptorWriter.write( md, ivyfile );

            final artifacts = resolve( ivyfile.toURL())
            final gavc      = "$groupId:$artifactId:$version:${ classifier ? classifier + ':' : '' }$type"

            if ( ! artifacts )
            {
                warnOrFail( "Failed to resolve [$gavc]" )
                return null
            }

            if ( artifacts.size() > 1 )
            {
                warnOrFail( "Multiple artifacts resolved for [$gavc] - [${ artifacts }], specify <classifier> pattern." )
            }

            artifact( groupId, artifactId, version, type, classifier, artifacts.first().file )
        }
        finally
        {
            GCommons.file().delete( ivyfile )
        }
    }


    /**
     * Resolves dependencies specified in Ivy file.
     *
     * @param ivyFile ivy dependencies file
     *
     * @return Maven artifacts resolved or empty list if resolution fails.
     * @throws RuntimeException if artifacts resolution fails and {@link #failOnError} is {@code true}.
     */
    @Requires({ ivyFile })
    @Ensures({ result.every{ it.file.file } })
    @SuppressWarnings( 'GroovySetterCallCanBePropertyAccess' )
    List<Artifact> resolve ( URL ivyFile )
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
                          "[${ artifactsNumber( report.artifacts )} of " +
                          "[${ report.dependencies.size() }] dependenc${ report.dependencies.size() == 1 ? 'y' : 'ies' }"  )
            }
        }

        if ( report.unresolvedDependencies )
        {
            warnOrFail( "Failed to resolve [$ivyFile] dependencies: ${ report.unresolvedDependencies }" )
        }

        if ( report.allProblemMessages )
        {
            warnOrFail( "Errors found when resolving [$ivyFile] dependencies: ${ report.allProblemMessages }" )
        }

        final artifactsReports = report.allArtifactsReports

        if ( ! artifactsReports )
        {
            warnOrFail( "Failed to resolve [$ivyFile] dependencies: no artifacts resolved." )
            return []
        }

        final artifacts = artifactsReports.collect {
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
            File   f          = verify().file( artifactReport.localFile )

            artifact( IvyMojo.IVY_PREFIX + groupId, artifactId, version, file().extension( f ), classifier, f )
        }

        if ( verbose )
        {
            log.info( "[$ivyFile] - ${ artifactsNumber( artifacts )} resolved: ${ artifactsToString( artifacts )}" )
        }

        artifacts
    }


    /**
     * Retrieves a String-ified representation of artifacts number: "[1] artifact", "[5] artifacts", etc.
     *
     * @param  l artifacts to String-ify
     * @return String-ified representation of artifacts number
     */
    @Requires({ l })
    @Ensures({ result })
    String artifactsNumber( List<Artifact> l )
    {
        "[${ l.size()}] artifact${ general().s( l.size())}"
    }


    /**
     * Retrieves a String-ified representation of artifacts specified.
     *
     * @param  l artifacts to String-ify
     * @return String-ified representation of artifacts specified
     */
    @Requires({ l })
    @Ensures({ result })
    String artifactsToString( List<Artifact> l )
    {
        SEPARATOR + l.collect{ "$it - [$it.file.canonicalPath]" }.join( SEPARATOR )
    }


    /**
     * Logs an error message or throws a fatal exception, according to {@link #failOnError} field.
     * @param errorMessage error message to log or throw
     */
    @Requires({ errorMessage })
    void warnOrFail ( String errorMessage )
    {
        if ( failOnError ) { throw new RuntimeException( errorMessage )}
        else               { log.warn( errorMessage ) }
    }
}
