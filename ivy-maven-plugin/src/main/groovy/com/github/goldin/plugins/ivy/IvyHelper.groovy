package com.github.goldin.plugins.ivy

import static com.github.goldin.plugins.common.ConversionUtils.*
import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultIncludeRule
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.MDArtifact
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ArtifactDownloadReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.matcher.ExactOrRegexpPatternMatcher
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorWriter
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Contains various Ivy-related helper methods.
 */
class IvyHelper
{
    static final String SEPARATOR  = '\n * '

    final IvyMojo mojo
    final Ivy     ivy
    final URL     ivyconfUrl
    final boolean verbose
    final boolean failOnError
    final boolean offline


    /**
     * Creates new instance of this helper class.
     *
     * @param ivyconfUrl  Ivy settings file {@link URL}.
     * @param verbose     whether logging should be verbose.
     * @param failOnError whether execution should fail when resolving Ivy artifacts fails.
     * @param offline     whether Maven build operates in offline mode.
     */
    @Requires({ mojo && ivyconfUrl })
    IvyHelper ( IvyMojo mojo, URL ivyconfUrl, boolean verbose, boolean failOnError, boolean offline )
    {
        IvySettings settings = new IvySettings()
        settings.load( ivyconfUrl )

        this.mojo        = mojo
        this.ivy         = Ivy.newInstance( settings )
        this.ivyconfUrl  = ivyconfUrl
        this.verbose     = verbose
        this.failOnError = failOnError
        this.offline     = offline
    }


    /**
     * Resolves dependency specified.
     *
     * @param groupId    dependency {@code <groupId>}
     * @param artifactId dependency {@code <artifactId>}
     * @param version    dependency {@code <version>}
     * @param type       dependency {@code <type>}
     * @param classifier dependency {@code <classifier>}, may be null
     *
     * @return Maven artifact resolved,
     *         its "file" field ({@link Artifact#getFile}) may be not set
     *         if artifact resolution fails and {@link #failOnError} is {@code false}.
     * @throws RuntimeException if artifact resolution fails and {@link #failOnError} is {@code true}.
     */
    @Requires({ groupId && artifactId && version && type })
    @Ensures({ result })
    Artifact resolve ( String groupId, String artifactId, String version, String type, String classifier )
    {
        if ( groupId.startsWith( IVY_PREFIX ))
        {
            groupId = groupId.substring( IVY_PREFIX.size())
        }

        final ivyfile = File.createTempFile( 'ivy', '.xml' )
        ivyfile.deleteOnExit();

        try
        {
            final md      = DefaultModuleDescriptor.newDefaultInstance( ModuleRevisionId.newInstance( groupId, artifactId + '-caller', 'working' ))
            final module  = ModuleRevisionId.newInstance( groupId, artifactId, version )
            final dd      = new DefaultDependencyDescriptor( md, module, false, false, true )
            dd.addIncludeRule( '', new DefaultIncludeRule( new ArtifactId( module.moduleId, classifier ?: '.*', type, type ),
                                                           new ExactOrRegexpPatternMatcher(),
                                                           [:] ))
            md.addDependency( dd );
            XmlModuleDescriptorWriter.write( md, ivyfile );

            final gavc      = "$groupId:$artifactId:$version:$type${ classifier ? ':' + classifier  : '' }"
            final artifacts = resolve( ivyfile.toURL(), false )

            if ( artifacts.size() != 1 )
            {
                failOrWarn( artifacts ?
                    "Multiple artifacts resolved for [$gavc] - ${ artifacts }, consider adding <classifier> such as <classifier>${ artifacts*.classifier.find{ it }}</classifier>" :
                    "Failed to resolve [$gavc] artifact" )
            }

            toMavenArtifact( groupId, artifactId, version, '', type,
                             '' /* <classifier> is only used to name an Ivy artifact, this is not a real Maven <classifier> */,
                             false,
                             ( artifacts ? artifacts.first().file : null ))
        }
        finally
        {
            fileBean().delete( ivyfile )
        }
    }


    /**
     * Resolves dependencies specified in Ivy file.
     *
     * @param ivyFile      ivy dependencies file.
     * @param reportErrors whether resolution errors should be reported.
     *
     * @return Maven artifacts resolved or empty list if resolution fails.
     * @throws RuntimeException if artifacts resolution fails and {@link #failOnError} is {@code true}.
     */
    @Requires({ ivyFile })
    @Ensures({ result != null })
    @SuppressWarnings( 'GroovySetterCallCanBePropertyAccess' )
    List<Artifact> resolve ( URL ivyFile, boolean reportErrors = true )
    {
        final options        = new ResolveOptions()
        options.confs        = [ 'default' ] as String[]
        options.log          = verbose ? 'default' : 'download-only'
        options.useCacheOnly = offline
        final report         = ivy.resolve( ivyFile, options )

        if ( verbose )
        {
            if ( report.downloadSize < 1 )
            {
                log.info( "[${ report.downloadSize }] bytes downloaded" )
            }
            else
            {
                log.info( "[${( long )( report.downloadSize / 1024 ) }] Kb downloaded in [${ ( long ) ( report.downloadTime / 1000 ) }] sec - " +
                          "${ artifactsNumber( report.artifacts )} of " +
                          "[${ report.dependencies.size() }] dependenc${ report.dependencies.size() == 1 ? 'y' : 'ies' }"  )
            }
        }

        if ( report.unresolvedDependencies && reportErrors )
        {
            failOrWarn( "Failed to resolve [$ivyFile] dependencies: ${ report.unresolvedDependencies }" )
        }

        if ( report.allProblemMessages && reportErrors )
        {
            failOrWarn( "Errors found when resolving [$ivyFile] dependencies: ${ report.allProblemMessages }" )
        }

        final artifactsReports = report.allArtifactsReports

        if ( ! artifactsReports )
        {
            if ( reportErrors ) { failOrWarn( "Failed to resolve [$ivyFile] dependencies: no artifacts resolved" ) }
            return []
        }

        List<Artifact> artifacts = artifactsReports.collect {
            ArtifactDownloadReport artifactReport ->
            assert artifactReport.artifact instanceof MDArtifact

            /**
             * See http://db.tt/9Cf1X4bH for debug view of the object received.
             */
            Map<String, String> attributes = (( MDArtifact ) artifactReport.artifact ).md.metadataArtifact.id.moduleRevisionId.attributes
            String groupId    = verifyBean().notNullOrEmpty( attributes.organisation )
            String artifactId = verifyBean().notNullOrEmpty( attributes.module )
            String version    = verifyBean().notNullOrEmpty( attributes.revision )

            if ( artifactReport.artifactOrigin?.artifact?.name && artifactReport.localFile )
            {
                // artifact name ("core/annotations" - http://goo.gl/se95h) plays as a classifier
                String classifier = artifactReport.artifactOrigin.artifact.name
                File   f          = verifyBean().file( artifactReport.localFile )
                String type       = fileBean().extension( f )
                toMavenArtifact( IVY_PREFIX + groupId, artifactId, version, '', type, classifier, false, f )
            }
            else
            {
                if ( reportErrors )
                {
                    failOrWarn( "Failed to resolve [$ivyFile] dependencies: partially resolved artifactReport" )
                }
                null // Partially resolved artifactReport can't be converted to Maven Artifact
            }
        }.
        grep() // Filter out partially resolved artifactReports

        if ( verbose )
        {
            log.info( "[$ivyFile] - ${ artifactsNumber( artifacts )} resolved" +
                      ( artifacts ? ':' + artifactsToString( artifacts ) : '' ))
        }

        assert artifacts.every{ it.file.file }
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
        "[${ l.size()}] artifact${ generalBean().s( l.size())}"
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
     * Adds artifacts to the scope specified.
     *
     * @param scope     Maven scope to add artifacts to: "compile", "runtime", "test", etc.
     * @param artifacts dependencies to add to the scope
     * @param project   current Maven project
     */
    @Requires({ artifacts && scopes })
    void addToScopes ( List<Artifact> artifacts, String scopes )
    {
        assert artifacts && scopes && artifacts.every{ it.file.file }

        split( scopes ).each {
            String scope ->

           /**
             * Adding jars to Maven's scope and compilation classpath.
             */
            artifacts.each {
                Artifact a ->
                a.scope = scope
                assert a.artifactHandler instanceof DefaultArtifactHandler
                (( DefaultArtifactHandler ) a.artifactHandler ).addedToClasspath = true
            }

            mojo.project.resolvedArtifacts = new HashSet<Artifact>( mojo.project.resolvedArtifacts + artifacts )
        }
    }


    /**
     * Copies artifacts to directory specified.
     *
     * @param directory directory to copy the artifacts to
     * @param artifacts artifacts to copy
     * @param verbose   whether copy operation should be logged
     */
    @Requires({ artifacts && directory })
    void copyToDir ( List<Artifact> artifacts, File directory, boolean verbose )
    {
        assert artifacts && directory && artifacts.every{ it.file.file }

        artifacts.each {
            Artifact a ->
            File destination = fileBean().copy( a.file, directory )

            if ( verbose )
            {
                log.info( "$a - [$a.file.canonicalPath] copied to [$destination.canonicalPath]" )
            }
        }

        assert artifacts.every{ new File( directory, it.file.name ).file }
    }


    /**
     * Logs an error message or throws a fatal exception, according to {@link #failOnError} field.
     * @param errorMessage error message to log or throw
     */
    @Requires({ errorMessage })
    void failOrWarn ( String errorMessage )
    {
        failOrWarn( failOnError, errorMessage + ( offline ? ' (system is in offline mode).' : '.' ))
    }
}
