package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.gcommons.beans.ExecOption
import com.github.goldin.gcommons.util.GroovyConfig
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.NetworkUtils
import com.github.goldin.plugins.common.Replace
import groovy.io.FileType
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.*
import org.apache.maven.project.MavenProjectHelper
import org.apache.maven.shared.filtering.MavenFileFilter
import org.codehaus.plexus.util.FileUtils
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * MOJO copying resources specified.
 */
@Mojo( name = 'copy', defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])

class CopyMojo extends BaseGroovyMojo
{
   /**
    * Container-injected fields
    */

    @Component
    private MavenProjectHelper mavenProjectHelper

    @Component ( role = MavenFileFilter, hint = 'default' )
    private MavenFileFilter fileFilter

    /**
     * User-provided fields
     */

    @Parameter
    private CopyManifest manifest = new CopyManifest()

    @Parameter ( required = false )
    private boolean skipIdentical = false

    @Parameter ( required = false )
    private boolean skipIdenticalUseChecksum = false

    @Parameter ( required = false )
    private boolean skipPacked = false

    @Parameter ( required = false )
    private boolean skipUnpacked = false

    @Parameter ( required = false )
    private boolean stripVersion = false

    @Parameter ( required = false )
    private boolean stripTimestamp = false

    @Parameter ( required = false )
    private boolean eliminateDuplicates = true

    @Parameter ( required = false )
    private boolean parallelDownload = false

    @Parameter ( required = false )
    private String customArchiveFormats

    /**
     * "false" or comma-separated list of default excludes
     * Not active for Net operations
     */
    @Parameter ( required = false )
    private String  defaultExcludes
    String         defaultExcludes()
    {
        this.defaultExcludes ?:
        (([ '**/.settings/**', '**/.classpath', '**/.project', '**/*.iws', '**/*.iml', '**/*.ipr' ] +
           fileBean().defaultExcludes + ( FileUtils.defaultExcludes as List )) as Set ).sort().join( ',' )
    }

    @Parameter ( required = false )
    private boolean verbose = true

    @Parameter ( required = false )
    private boolean filterWithDollarOnly = false

    @Parameter ( required = false )
    private String nonFilteredExtensions

    @Parameter ( required = false )
    private boolean failIfNotFound = true

    @Parameter ( required = false )
    private boolean failOnError = true

    @Parameter ( required = false )
    private boolean useTrueZipForPack = false

    @Parameter ( required = false )
    private boolean useTrueZipForUnpack = true

    @Parameter ( required = false )
    private CopyResource[] resources

    @Parameter ( required = false )
    private CopyResource resource

    private List<CopyResource> resources () { generalBean().list( this.resources, this.resource ) }

    @Parameter ( required = false )
    private GroovyConfig groovyConfig


    private final CopyMojoHelper helper = new CopyMojoHelper( this )


    /**
     * Predefined {@code <filter>} values:
     * Key   - {@code <filter>} value
     * Value - Groovy expression to use
     */
    private static final Map<String, String> FILTERS = [ '{{latest}}' :
        '''
        assert files
        def file         = files[ 0 ]
        def lastModified = file.lastModified()
        if ( files.size() > 1 )
        {
            for ( f in files[ 1 .. -1 ] )
            {
                if ( f.lastModified() > lastModified )
                {
                    lastModified = f.lastModified()
                    file         = f
                }
            }
        }
        file''' ]


    /**
     * Copies the Resources specified
     */
    @Override
    @SuppressWarnings([ 'AbcComplexity', 'CatchThrowable' ])
    void doExecute()
    {
        final  resources = resources()
        assert resources, "No <resource> or <resources> provided"

        updateCustomArchiveFormats()

        for ( CopyResource resource in resources )
        {
            resource.with {

                boolean failed = false

                try
                {
                    startTime = System.currentTimeMillis()
                    processResource( resource )
                }
                catch( Throwable e )
                {
                    failed              = true
                    String errorMessage = "Processing <resource> [$resource] ${ failsWith ? 'expectedly ' : '' }failed with [${ e.class.name }]"

                    if ( failsWith )
                    {
                        if ( ! e.class.name.endsWith( failsWith ))
                        {
                            throw new MojoExecutionException(
                                "Resource [$resource] should have failed with [$failsWith], failed with [$e] instead",
                                e )
                        }
                    }
                    else if ( generalBean().choose( failOnError, this.failOnError ))
                    {
                        throw new MojoExecutionException( errorMessage, e )
                    }

                    ( failsWith ? log.&info : log.&warn )( errorMessage )
                }

                if ( failsWith && ( ! failed ))
                {
                    throw new MojoExecutionException( "Resource [$resource] should have failed with [$failsWith] but it didn't" )
                }

                if ( stop )
                {
                    /**
                     * Used for troubleshooting purposes only
                     */
                    log.info( '''
                              ------------------------------------------------
                                *** Build stopped with <stop>true</stop> ***
                              ------------------------------------------------'''.stripIndent())
                    System.exit( 0 )
                }
            }
        }
    }


    void updateCustomArchiveFormats ( )
    {
        if ( customArchiveFormats )
        {
            fileBean().customArchiveFormats = customArchiveFormats.readLines()*.trim().grep().
                                              inject( [:].withDefault{ [] }){
                Map m, String line ->
                def ( String format, String extensions ) = line.tokenize( '=' )*.trim()
                (( List ) m[ format ] ).addAll( extensions.tokenize( ',' )*.trim().grep())
                m
            }
        }
    }


    @Requires({ resource })
    private void processResource( CopyResource resource )
    {
        final isVerbose        = generalBean().choose( resource.verbose,        this.verbose        )
        final isFailIfNotFound = generalBean().choose( resource.failIfNotFound, this.failIfNotFound )

        resource.with {
            if ( runIf( runIf ))
            {
                final descriptionEval  = {
                    assert description
                    description.trim().with{ ( startsWith( '{{' ) && endsWith( '}}' )) ? eval(( String ) delegate, String ) : delegate }
                }

                if ( description )
                {
                    log.info( "==> Processing <resource> [${ descriptionEval() }]" )
                }

                boolean processed = false
                directory         = canonicalPath ( directory )
                includes          = helper.updatePatterns( directory, includes, encoding )
                excludes          = helper.updatePatterns( directory, excludes, encoding )

                if ( mkdir || directory )
                {
                    processFilesResource( resource, isVerbose, isFailIfNotFound )
                    processed = true
                }

                if ( dependencies())
                {
                    processDependenciesResource( resource, isVerbose, isFailIfNotFound )
                    processed = true
                }

                assert processed, "Don't know how to process <resource> [$resource] - is it configured correctly?"

                endTime = (( endTime   > 0 ) ? endTime : System.currentTimeMillis())
                assert     ( startTime > 0 ) && ( endTime >= startTime )

                if ( description )
                {
                    log.info( "==> Processing <resource> [${ descriptionEval() }] - done, [${ endTime - startTime }] ms" )
                }
            }
        }
    }


    /**
     * Handles resource specified.
     *
     * @param resource        resource to handle
     * @param verbose         verbose logging
     * @param failIfNotFound  whether execution should fail if no files were matched
     */
    @Requires({ resource })
    private void processFilesResource ( CopyResource resource, boolean verbose, boolean failIfNotFound )
    {
        assert ( resource.mkdir || resource.directory )

        final isDownload      = netBean().isNet( resource.directory )
        final isUpload        = netBean().isNet( resource.targetPaths())
        File  sourceDirectory = ( resource.directory ? new File( resource.directory ) : null ) // null for <mkdir> operation
        final tempDirectory   = null

        try
        {
            def( List<String> includes, List<String> excludes ) = [ resource.includes, resource.excludes ].collect {
                ( isDownload || ( ! it )) ? null : new ArrayList<String>( it )
                // Download operation or null/empty list - all files are included, none excluded
            }

            if ( ! resource.mkdir )
            {
                if ( isDownload )
                {
                    tempDirectory = fileBean().tempDirectory()
                    DownloadHelper.download( resource, resource.directory, tempDirectory, verbose, groovyConfig )

                    assert ( tempDirectory.list() || ( ! failIfNotFound )), \
                           "No files were downloaded from [$resource.directory] " +
                           "and include/exclude patterns ${ resource.includes ?: [] }/${ resource.excludes ?: [] }"

                    sourceDirectory = tempDirectory
                }
                else
                {
                    // Adding defaultExcludes to excludes if not disabled with "false" value
                    if  (( resource.defaultExcludes != 'false' ) && ( defaultExcludes() != 'false' ))
                    {
                        excludes = ( excludes ?: [] ) + split( resource.defaultExcludes ?: defaultExcludes())
                    }

                    if ( isUpload )
                    {
                        assert ( ! resource.process ), "<process> is not supported for remote uploads"

                        if ( resource.needsProcessingBeforeUpload())
                        {
                            assert ( ! resource.with { clean || mkdir || pack || unpack }), \
                                   "[$resource] - no <clean>, <mkdir>, <pack> or <unpack> operation allowed when uploading files to ${resource.targetPaths()}, " +
                                   "use a separate <resource> for that"

                            tempDirectory = fileBean().tempDirectory()

                            processFilesResource( resource.makeCopy( this, tempDirectory, sourceDirectory, includes, excludes, true ),
                                                  verbose, failIfNotFound )

                            sourceDirectory = tempDirectory  // Files are now uploaded from the temp directory.
                            includes        = null           // All files are includes and none is excluded.
                            excludes        = null           //
                        }

                        NetworkUtils.upload( resource.targetPaths(),
                                             sourceDirectory,
                                             includes,
                                             excludes,
                                             resource.preservePath,
                                             verbose,
                                             failIfNotFound,
                                             generalBean().choose( resource.skipIdentical,            skipIdentical ),
                                             generalBean().choose( resource.skipIdenticalUseChecksum, skipIdenticalUseChecksum ))
                        return
                    }
                }
            }

            processFilesResource( resource, sourceDirectory, includes, excludes, verbose, failIfNotFound )
        }
        finally
        {
            /* If arguments not specified explicitly as array may fail with "IllegalArgumentException: wrong number of arguments" */
            if ( tempDirectory ){ fileBean().delete([ tempDirectory ] as File[] )}
        }
    }


    /**
     * Handles resource {@code <dependencies>}.
     *
     * @param resource       resource to handle
     * @param verbose        verbose logging
     * @param failIfNotFound whether execution should fail if zero dependencies were resolved
     */
    @Requires({ resource })
    private void processDependenciesResource ( final CopyResource resource, final boolean verbose, final boolean failIfNotFound )
    {
        List<CopyDependency> resourceDependencies = verifyBean().notNullOrEmpty( resource.dependencies())
        final dependenciesAtM2           = resource.dependenciesAtM2()
        final isSkipIdentical            = generalBean().choose( resource.skipIdentical,            this.skipIdentical )
        final isSkipIdenticalUseChecksum = generalBean().choose( resource.skipIdenticalUseChecksum, this.skipIdenticalUseChecksum )
        final isStripVersion             = generalBean().choose( resource.stripVersion,             this.stripVersion  )
        final isStripTimestamp           = generalBean().choose( resource.stripTimestamp,           this.stripTimestamp )
        final eliminateDuplicates        = generalBean().choose( resource.eliminateDuplicates,      this.eliminateDuplicates )
        final parallelDownload           = generalBean().choose( resource.parallelDownload,         this.parallelDownload  )

        if ( dependenciesAtM2 )
        {
            boolean resolved = false // Whether any dependency was resolved

            resolve( resourceDependencies, eliminateDuplicates, parallelDownload, verbose, failIfNotFound ).each {
                CopyDependency d ->

                resolved = true

                /**
                 * File may be resolved from other module "target" (that is built in the same reactor),
                 * not necessarily from ".m2"
                 */
                File f = verifyBean().file( d.artifact.file ).canonicalFile

                (( CopyResource ) resource.clone()).with {

                    directory                = f.parent
                    includes                 = [ f.name ]
                    skipIdentical            = isSkipIdentical
                    skipIdenticalUseChecksum = isSkipIdenticalUseChecksum
                    dependencies             = null
                    dependency               = null
                    destFileName  =
                        ( d.destFileName && ( d.destFileName != f.name )) ? d.destFileName : /* the one from <dependency> but not default one, set by Maven */
                        ( destFileName )                                  ? destFileName   : /* the one from <resource> */
                                                                            f.name
                    if ( d.stripVersion || isStripVersion )
                    {
                        if ( d.version.endsWith( '-SNAPSHOT' ))
                        {
                            final version    = d.version.substring( 0, d.version.lastIndexOf( '-SNAPSHOT' ))
                            final classifier = d.classifier.with              { delegate ? "-$delegate" : '' }
                            final extension  = fileBean().extension( f ).with { delegate ? ".$delegate" : '' }
                            destFileName     = destFileName.replaceAll( ~/-\Q$version\E.+?\Q$classifier$extension\E$/,
                                                                        "$classifier$extension".toString())
                        }
                        else
                        {
                            destFileName = destFileName.replace( "-${ d.version }", '' )
                        }
                    }
                    else if ( d.stripTimestamp || isStripTimestamp )
                    {
                        destFileName = helper.stripTimestampFromVersion( destFileName )
                    }

                    processFilesResource(( CopyResource ) delegate, verbose, true )
                }
            }

            if ( resolved ) { return }
        }

        final tempDirectory = fileBean().tempDirectory()

        try
        {
            if ( ! dependenciesAtM2 )
            {
                resolve( resourceDependencies, eliminateDuplicates, parallelDownload, verbose, failIfNotFound, isStripVersion, isStripTimestamp ).each {
                    CopyDependency d -> fileBean().copy( d.artifact.file, tempDirectory, d.destFileName )
                }
            }

            /**
             * Even if zero dependencies were copied to temp (they were all excluded or
             * optional that failed to resolve) we still copy them to the destination due
             * to possible <process> or any other post-processing involved.
             */

            (( CopyResource ) resource.clone()).with {

                directory                = tempDirectory
                includes                 = [ '**' ]
                skipIdentical            = isSkipIdentical
                skipIdenticalUseChecksum = isSkipIdenticalUseChecksum
                dependencies             = null
                dependency               = null

                processFilesResource(( CopyResource ) delegate, verbose, ( tempDirectory.listFiles().size() > 0 ))
            }
        }
        finally
        {
            fileBean().delete(( File ) /* Occasionally fails with "Wrong number of arguments" otherwise */ tempDirectory )
        }
    }


    /**
     * Resolves and filters resource dependencies.
     *
     * @param inputDependencies   dependencies to resolve and filter
     * @param eliminateDuplicates whether duplicate dependencies should be removed from result
     * @param parallelDownload    whether dependencies should be downloaded in parallel
     * @param verbose             whether resolving process should be logged
     * @param failIfNotFound      whether execution should fail if zero artifacts were resolved
     * @param stripVersion        whether dependencies version should be stripped
     * @param stripTimestamp      whether dependencies snapshot timestamp should be stripped
     * @return                    dependencies resolved and filtered
     */
    @Requires({ inputDependencies })
    @Ensures ({ result != null })
    private Collection<CopyDependency> resolve ( List<CopyDependency> inputDependencies,
                                                 boolean              eliminateDuplicates,
                                                 boolean              parallelDownload,
                                                 boolean              verbose,
                                                 boolean              failIfNotFound,
                                                 boolean              stripVersion   = false,
                                                 boolean              stripTimestamp = false )
    {
        final result = helper.resolveDependencies( inputDependencies, eliminateDuplicates, parallelDownload, verbose, failIfNotFound ).
        findAll { CopyDependency d -> d.artifact?.file?.file }. // Filtering out (optional) unresolved artifacts
        collect { CopyDependency d ->

            d.destFileName = d.destFileName ?:
                             ( d.groupId.startsWith( IVY_PREFIX )) ?
                               /**
                                * Ivy dependencies may carry a fake <classifier> (serving as a pattern to name the artifact)
                                * in "destFileName" which now needs to be removed.
                                */
                                d.artifact.file.name :
                                helper.artifactFileName( d.artifact,
                                                         ( d.stripVersion   || stripVersion   ),
                                                         ( d.stripTimestamp || stripTimestamp ))
            d
        }

        assert ( result || ( ! failIfNotFound ) || inputDependencies.every { it.optional }), "No dependencies resolved with $inputDependencies"
        assert result.every { it.artifact.file.file }
        result
    }


    /**
     * Handles the resource specified.
     *
     * @param resource        resource to handle: copy/unpack/pack/clean/mkdir with filtering and replacements
     * @param sourceDirectory base directory of files to perform an operation on
     * @param includes        include patterns of files
     * @param excludes        exclude patterns of file
     * @param verbose         whether verbose logging should be used when file is copied
     * @param failIfNotFound  whether execution should fail if no files were matched
     */
    @Requires({ resource })
    private CopyResource processFilesResource ( CopyResource resource,
                                                File         sourceDirectory,
                                                List<String> includes        = null,
                                                List<String> excludes        = null,
                                                boolean      verbose         = true,
                                                boolean      failIfNotFound  = true )
    {
        final zipEntries        = resource.zipEntries()
        final zipEntriesExclude = resource.zipEntriesExclude()
        final manifest          = resource.manifest ? resource.manifest.add( this.manifest ) : this.manifest

        if ( manifest.entries )
        {
            assert ( resource.pack || ( ! resource.manifest )), '<manifest> can only be used with <pack> operation'
        }

        if ( zipEntries        ) { assert resource.unpack, '<zipEntry> or <zipEntries> can only be used with <unpack>true</unpack>' }
        if ( zipEntriesExclude ) { assert resource.unpack, '<zipEntryExclude> or <zipEntriesExclude> can only be used with <unpack>true</unpack>' }
        if ( resource.prefix   ) { assert resource.pack,   '<prefix> can only be used with <pack>true</pack>' }

        List<File> filesToProcess = []

        if ( resource.clean )
        {
            filesToProcess.addAll( clean( sourceDirectory, includes, excludes, resource.cleanEmptyDirectories, resource.filter, verbose, failIfNotFound ))
        }
        else
        {
            if (( ! resource.mkdir ) && failIfNotFound )
            {
                verifyBean().directory( sourceDirectory )
            }

            for ( path in resource.targetPaths())
            {
                if ( resource.mkdir )
                {
                    final directory = mkdir( path, verbose )
                    if ( directory ){ filesToProcess << directory } // null when remote directory created
                }

                File targetPath = new File( verifyBean().notNullOrEmpty( path ))

                if ( resource.pack )
                {
                    final manifestDir = ( manifest.entries ? helper.prepareManifest( manifest ) : null )
                    pack( resource, sourceDirectory, targetPath, includes, excludes, failIfNotFound, manifestDir )?.with{ filesToProcess << delegate }
                    if ( manifestDir ) { fileBean().delete( manifestDir ) }
                }
                else if ( sourceDirectory /* null when mkdir is performed */ )
                {
                    final files = fileBean().files( sourceDirectory, includes, excludes, true, false, failIfNotFound )
                    for ( filteredFile in filter( files, resource.filter, verbose, failIfNotFound ))
                    {
                        fileBean().mkdirs( targetPath )

                        if ( resource.unpack )
                        {
                            filesToProcess.addAll( unpack( resource, filteredFile, targetPath, zipEntries, zipEntriesExclude, verbose, failIfNotFound ))
                        }
                        else
                        {
                            copyResourceFile( resource, sourceDirectory, filteredFile, targetPath, verbose )?.with { filesToProcess << delegate }
                        }
                    }
                }
            }
        }

        assert ( resource.startTime > 0 )
        resource.endTime = System.currentTimeMillis()
        process( filesToProcess, resource.chmod, resource.process, resource.clean, ( resource.endTime - resource.startTime ))
        resource
    }


    /**
     * Calculates new name of the file to copy taking its resource into consideration.
     *
     * @param f        file to copy
     * @param resource file's <resource>
     * @return         file's new name
     */
    @Requires({ f && resource })
    @Ensures({ result })
    private String newName ( File f, CopyResource resource )
    {
        resource.with {

            if ( destFileName ) { return destFileName }

            String newName = f.name

            if ( destFilePrefix || destFileSuffix || destFileExtension )
            {
                assert ( targetPaths() && directory && ( ! ( pack || unpack ))), \
                       '<destFilePrefix>, <destFileSuffix>, <destFileExtension> can only be used when files are copied from <targetPath> to <directory>'

                String extension = fileBean().extension( f )
                String body      = ( extension ? newName.substring( 0, newName.size() - extension.size() - 1 ) : newName )
                extension        = destFileExtension  ?: extension
                newName          = "${ destFilePrefix ?: '' }${ body }${ destFileSuffix ?: '' }${ extension ? '.' + extension : '' }"
            }

            verifyBean().notNullOrEmpty( newName )
        }
    }


    /**
     * Copies the file specified.
     *
     * @param resource        current copy resource
     * @param sourceDirectory file base directory
     * @param sourceFile      file to copy
     * @param targetPath      target location to copy the file to
     * @param verbose         verbose logging
     * @return file copied if copying was performed, null otherwise
     */
    @Requires({ resource && sourceDirectory.directory && sourceFile.file && targetPath.directory })
    @Ensures({ ( result == null ) || ( result.file ) })
    private File copyResourceFile ( CopyResource resource,
                                    File         sourceDirectory,
                                    File         sourceFile,
                                    File         targetPath,
                                    boolean      verbose )
    {
        assert ! netBean().isNet( sourceDirectory.path )
        assert ! netBean().isNet( targetPath.path )

        String  newName  = newName( sourceFile, resource )
        boolean noFilter = split(( resource.nonFilteredExtensions ?: nonFilteredExtensions ?: '' ).toLowerCase()).
                           contains( fileBean().extension( new File( newName )).toLowerCase())
        String  newPath  = resource.preservePath ? fileBean().relativePath( sourceDirectory, new File( sourceFile.parentFile, newName )) : newName
        File    file     = new File( targetPath, newPath )

        assert file.canonicalPath.endsWith( newName )

        helper.copyFile( sourceFile.canonicalFile,
                         file.canonicalFile,
                         generalBean().choose( resource.skipIdentical,            skipIdentical ),
                         generalBean().choose( resource.skipIdenticalUseChecksum, skipIdenticalUseChecksum ),
                         ( noFilter ? [] : resource.replaces()) as Replace[],
                         (( ! noFilter ) && resource.filtering ),
                         resource.encoding,
                         fileFilter,
                         verbose,
                         resource.move,
                         generalBean().choose( resource.filterWithDollarOnly, filterWithDollarOnly ))
    }


    /**
     * Packs directory specified.
     *
     * @param resource        current copy resource
     * @param sourceDirectory directory to pack
     * @param targetArchive   target archie to pack the directory to
     * @param includes        files to include, may be <code>null</code>
     * @param excludes        files to exclude, may be <code>null</code>
     * @param failIfNotFound  fail if directory not found or no files were included
     * @param manifestDir     directory where Manifest file to be packed is stored
     *
     * @return target archive packed or null if no file was packed
     */
    @SuppressWarnings( 'AbcComplexity' )
    @Requires({ resource && sourceDirectory && targetArchive })
    @Ensures({ ( result == null ) || ( result.file ) })
    private File pack( CopyResource resource,
                       File         sourceDirectory,
                       File         targetArchive,
                       List<String> includes,
                       List<String> excludes,
                       boolean      failIfNotFound,
                       File         manifestDir )
    {
        if ( ! sourceDirectory.directory )
        {
            failOrWarn( failIfNotFound, "Directory [$sourceDirectory.canonicalPath] doesn't exist, no files will be packed to [${ targetArchive.canonicalPath }]" )
            return null
        }

        final packUsingTemp  = ( resource.replaces() || resource.filtering )
        final filesDirectory = packUsingTemp ? fileBean().tempDirectory() : sourceDirectory
        final skipPacked     = generalBean().choose( resource.skipPacked, this.skipPacked )

        if ( packUsingTemp )
        {
            processFilesResource( resource.makeCopy( this, filesDirectory, sourceDirectory, includes, excludes ),
                                  false, failIfNotFound )
        }

        fileBean().with {

            pack( filesDirectory, targetArchive, includes, excludes,
                  generalBean().choose( resource.useTrueZipForPack, useTrueZipForPack ),
                  failIfNotFound, resource.update,
                  split( resource.defaultExcludes ?: defaultExcludes()),
                  resource.destFileName, resource.prefix, ( ! skipPacked ), manifestDir, resource.compressionLevel )

            assert targetArchive.file
            if ( resource.move ) { delete( files( sourceDirectory, includes, excludes, true, false, failIfNotFound, true ) as File[] ) }
            if ( packUsingTemp ) { delete( filesDirectory ) }

            if ( resource.attachArtifact )
            {
                mavenProjectHelper.attachArtifact( project, extension( targetArchive ), resource.artifactClassifier, targetArchive )
            }
        }

        if ( resource.deploy )
        {
            String[] data = split( resource.deploy, '|' )

            assert data?.size()?.with{( it == 4 ) || ( it == 5 )}, \
                   "Failed to read <deploy> data [$resource.deploy]. " +
                   'It should be of the following form: "<deployUrl>|<groupId>|<artifactId>|<version>[|<classifier>]"'

            def ( String url, String groupId, String artifactId, String version ) =
                data[ 0 .. 3 ].collect { String s -> verifyBean().notNullOrEmpty( s ) }
            def classifier = (( data.size() == 5 ) ? verifyBean().notNullOrEmpty( data[ 4 ] ) : null )

            helper.deploy( targetArchive, url, groupId, artifactId, version, classifier )
        }

        verifyBean().file( targetArchive )
    }


    /**
     *
     * @param resource             resource to unpack
     * @param sourceArchive        archive to unpack
     * @param destinationDirectory directory to unpack the archive to
     * @param zipEntries           Zip entries to unpack, can be empty
     * @param zipEntriesExclude    Zip entries to unpack, can be empty
     * @param verbose              whether logging should be verbose
     * @param failIfNotFound       whether execution should fail if no files were matched
     *
     * @return list of files unpacked or empty list if no files were unpacked
     */
    @Requires({ resource && sourceArchive.file && destinationDirectory.directory && ( zipEntries != null ) && ( zipEntriesExclude != null ) })
    @Ensures({ result != null })
    private List<File> unpack ( CopyResource resource,
                                File         sourceArchive,
                                File         destinationDirectory,
                                List<String> zipEntries,
                                List<String> zipEntriesExclude,
                                boolean      verbose,
                                boolean      failIfNotFound )
    {
        boolean skipUnpacked = generalBean().choose( resource.skipUnpacked, skipUnpacked )

        if ( skipUnpacked && destinationDirectory.directory && destinationDirectory.listFiles())
        {
            if ( verbose )
            {
                log.info( "<skipUnpacked> is true, directory [$destinationDirectory.canonicalPath] is not empty - " +
                          "unpacking of [$sourceArchive.canonicalPath] was cancelled" )
            }

            return []
        }

        final   readFiles       = { File directory -> fileBean().files( directory, null, null, true, false, false ) }
        boolean unpackUsingTemp = ( resource.replaces() || resource.filtering )
        File    unpackDirectory = unpackUsingTemp ? fileBean().tempDirectory() : destinationDirectory
        final   previousFiles   = readFiles( destinationDirectory )

        ( zipEntries || zipEntriesExclude ) ?
            fileBean().unpackZipEntries( sourceArchive, unpackDirectory, zipEntries, zipEntriesExclude, resource.preservePath, failIfNotFound ) :
            fileBean().unpack( sourceArchive, unpackDirectory, generalBean().choose( resource.useTrueZipForUnpack, useTrueZipForUnpack ))

        if ( unpackUsingTemp )
        {
            processFilesResource( resource.makeCopy( this, destinationDirectory, unpackDirectory, null, null ), false, true )
            fileBean().delete( unpackDirectory )
        }

        ( readFiles( destinationDirectory ) - previousFiles )
    }


    /**
     * Creates the directory specified.
     *
     * @param path    path to directory to create, can be remote resource
     * @param verbose verbose logging
     *
     * @return target path created or {@code null} in case of remote resource
     */
    @Requires({ path })
    private File mkdir( String path, boolean verbose )
    {
        if ( netBean().isNet( path ))
        {
            NetworkUtils.createRemoteDirectories( path, [], verbose )
            null
        }
        else
        {
            final directory = new File( path )

            if ( directory.directory )
            {
                if ( verbose ){ log.info( "Directory [$directory.canonicalPath] already exists" )}
                return directory
            }

            fileBean().mkdirs( directory )
            if ( verbose ){ log.info( "Directory [$directory.canonicalPath] created" )}

            verifyBean().directory( directory )
        }
    }


    /**
     * Cleans up directory specified.
     *
     * @param sourceDirectory       directory to cleanup
     * @param includes              files to include, may be <code>null</code>
     * @param excludes              files to exclude, may be <code>null</code>
     * @param cleanEmptyDirectories whether empty directories should be cleaned in addition to files matched
     * @param filterExpression      files "filter" expression
     * @param verbose               verbose logging
     * @param failIfNotFound        fail if directory not found or no files were included
     */
    @Requires({ sourceDirectory })
    private List<File> clean( File         sourceDirectory,
                              List<String> includes,
                              List<String> excludes,
                              boolean      cleanEmptyDirectories,
                              String       filterExpression,
                              boolean      verbose,
                              boolean      failIfNotFound )
    {
        if ( ! sourceDirectory.directory )
        {
            failOrWarn( failIfNotFound, "Directory [$sourceDirectory.canonicalPath] doesn't exist, no files will be deleted" )
            return []
        }

        List<File> files        = fileBean().files( sourceDirectory, includes, excludes, true, false, failIfNotFound )
        List<File> filesDeleted = filter( files, filterExpression, verbose, failIfNotFound )

        fileBean().delete( filesDeleted as File[] )

        if ( cleanEmptyDirectories )
        {
            List<File> directoriesDeleted = ( fileBean().recurse( sourceDirectory, [ type : FileType.DIRECTORIES ], {} ) + sourceDirectory ).
                                            findAll{ File f -> assert f.directory
                                                               fileBean().directorySize( f ) == 0 } /* Not all files could be deleted */
            fileBean().delete( directoriesDeleted as File[] )
            filesDeleted += directoriesDeleted
        }

        if ( verbose ) { log.info( "[$sourceDirectory.canonicalPath] files deleted: $filesDeleted" )}
        filesDeleted
    }


    /**
     * Filters files provided using Groovy expression specified.
     *
     * @param files               files to filter
     * @param filterExpression    Groovy expression, if <code>null</code> - no filtering is executed
     * @param verbose             whether logging should be verbose
     * @param failIfNotFound      whether execution should fail if <code>files</code> is empty
     *
     * @return new {@code Collection<File>} returned by Groovy
     *         or original <code>files</code> if <code>filterExpression</code> is null
     */
    @Requires({ files != null })
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    private List<File> filter( List<File> files,
                               String     filterExpression,
                               boolean    verbose,
                               boolean    failIfNotFound )
    {
        if  ( filterExpression )
        {
            verifyBean().file( files as File[] )

            String           expression    = verifyBean().notNullOrEmpty( FILTERS[ filterExpression ] ?: filterExpression )
            Object           o             = eval( expression, Object, groovyConfig, 'files', files, 'file', files ? files.first() : null )
            Collection<File> filesIncluded = (( o instanceof File       ) ? [ ( File ) o ]            :
                                              ( o instanceof Collection ) ? (( Collection<File> ) o ) :
                                                                            null )
            assert ( filesIncluded != null ), \
                   "<filter> expression [$expression] returned [$o] of type [${ o.getClass().name }] - should be File or Collection<File>"

            filesIncluded.each { assert ( it instanceof File ), \
                                 "<filter> expression [$expression] returned [$it] of type [${ it.getClass().name }] - should be File" }

            if ( verbose )
            {
                log.info( "Files left after applying <filter>:${ constantsBean().CRLF }${ stars( filesIncluded ) }" )
            }

            return filesIncluded as List
        }

        assert (( files ) || ( ! failIfNotFound )) // If not files are found - it should be handled by GCommons already
        files
    }


    /**
     * Processes files provided using Groovy expression specified.
     *
     * @param files             files to process
     * @param chmod             chmod to set
     * @param processExpression Groovy expression, if <code>null</code> - no processing is executed
     * @param verbose           whether logging should be verbose
     * @param isClean           whether files processed are of <clean> operation
     */
    @Requires({ ( files != null ) && ( time >= 0 ) })
    private void process( List<File> files, String chmod, String processExpression, boolean isClean, long time )
    {
        // noinspection GroovyAssignmentToMethodParameter
        files = files.toSet().sort()
        if ( ! isClean ){ verifyBean().exists( files as File[] )}

        if ( chmod && files )
        {
            exec( "chmod $chmod '${ files.join( "' '" )}'", basedir, true, false, ExecOption.CommonsExec )
        }

        if ( processExpression )
        {
            eval( processExpression, null, groovyConfig, 'files', files, 'file', files ? files.first() : null, 'time', time )
        }
    }
}
