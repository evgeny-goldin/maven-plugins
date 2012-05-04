package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.gcommons.GCommons
import com.github.goldin.gcommons.util.GroovyConfig
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.Replace
import groovy.io.FileType
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProjectHelper
import org.apache.maven.shared.filtering.MavenFileFilter
import org.codehaus.plexus.util.FileUtils
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.*


/**
 * MOJO copying resources specified.
 */
@MojoGoal( 'copy' )
@MojoPhase( 'package' )
@MojoRequiresDependencyResolution( 'test' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class CopyMojo extends BaseGroovyMojo
{
   /**
    * Container-injected fields
    */

    @MojoComponent
    public MavenProjectHelper mavenProjectHelper

    @MojoComponent ( role = 'org.apache.maven.shared.filtering.MavenFileFilter', roleHint = 'default' )
    public MavenFileFilter fileFilter

    /**
     * User-provided fields
     */

    @MojoParameter
    public  CopyManifest manifest = new CopyManifest()

    @MojoParameter ( required = false )
    public boolean skipIdentical = false

    @MojoParameter ( required = false )
    public boolean skipPacked = false

    @MojoParameter ( required = false )
    public boolean skipUnpacked = false

    @MojoParameter ( required = false )
    public boolean stripVersion = false

    /**
     * "false" or comma-separated list of default excludes
     * Not active for Net operations
     */
    @MojoParameter ( required = false )
    public String  defaultExcludes
    String         defaultExcludes()
    {
        this.defaultExcludes ?:
        (( [ '**/.settings/**', '**/.classpath', '**/.project', '**/*.iws', '**/*.iml', '**/*.ipr' ] +
           file().defaultExcludes + ( FileUtils.defaultExcludes as List )) as Set ).sort().join( ',' )
    }

    @MojoParameter ( required = false )
    public  boolean verbose = true

    @MojoParameter ( required = false )
    public  boolean filterWithDollarOnly = false

    @MojoParameter ( required = false )
    public  String nonFilteredExtensions

    @MojoParameter ( required = false )
    public  boolean failIfNotFound = true

    @MojoParameter ( required = false )
    public  boolean failOnError = true

    @MojoParameter ( required = false )
    public  boolean useTrueZipForPack = false

    @MojoParameter ( required = false )
    public  boolean useTrueZipForUnpack = true

    @MojoParameter ( required = false )
    public  CopyResource[] resources

    @MojoParameter ( required = false )
    public  CopyResource resource

    private List<CopyResource> resources () { general().list( this.resources, this.resource ) }

    @MojoParameter ( required = false )
    public GroovyConfig groovyConfig


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
                    String errorMessage = "Processing <resource> [$resource] ${ shouldFailWith ? 'expectedly ' : '' }failed with [${ e.class.name }]"

                    if ( shouldFailWith )
                    {
                        assert e.class.name.endsWith( shouldFailWith )
                    }
                    else if ( general().choose( failOnError, this.failOnError ))
                    {
                        throw new MojoExecutionException( errorMessage, e )
                    }

                    ( shouldFailWith ? log.&info : log.&warn )( errorMessage )
                }

                if ( shouldFailWith && ( ! failed ))
                {
                    throw new MojoExecutionException( "Resource [$resource] should have failed with [$shouldFailWith]" )
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


    @Requires({ resource })
    private void processResource( CopyResource resource )
    {
        final isVerbose        = general().choose( resource.verbose,        this.verbose        )
        final isFailIfNotFound = general().choose( resource.failIfNotFound, this.failIfNotFound )

        resource.with {
            if ( runIf( runIf ))
            {
                Closure d = { // Evaluates <description>
                    description.trim().with{ ( startsWith( '{{' ) && endsWith( '}}' )) ? eval(( String ) delegate, String ) : delegate }
                }

                if ( description )
                {
                    log.info( "==> Processing <resource> [${ d() }]" )
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

                assert processed, "Don't know how to process <resource> [$resource] - is it configured properly?"

                endTime = (( endTime   > 0 ) ? endTime : System.currentTimeMillis())
                assert     ( startTime > 0 ) && ( endTime >= startTime )

                if ( description )
                {
                    log.info( "==> Processing <resource> [${ d() }] - done, [${ endTime - startTime }] ms" )
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

        final isDownload      = net().isNet( resource.directory )
        final isUpload        = net().isNet( resource.targetPaths())
        final sourceDirectory = ( isDownload         ? file().tempDirectory()         : // Temp dir to download the files to
                                 resource.directory ? new File( resource.directory ) : // Directory to cleanup, upload or copy
                                                      null )                           // mkdir, no source directory
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
                    assert ( ! sourceDirectory.list()), \
                           "Temporal directory [$sourceDirectory.canonicalPath] for downloading files is not empty?"

                    NetworkUtils.download( resource, resource.directory, sourceDirectory, verbose, groovyConfig )

                    assert ( sourceDirectory.list() || ( ! failIfNotFound )), \
                           "No files were downloaded from [$resource.directory] " +
                           "and include/exclude patterns ${ resource.includes ?: [] }/${ resource.excludes ?: [] }"
                }
                else
                {   /**
                     * Default excludes are not active for downloaded files: all of them are included, none is excluded
                     */
                    if  (( resource.defaultExcludes != 'false' ) && ( defaultExcludes() != 'false' ))
                    {
                        excludes = ( excludes ?: [] ) + split( resource.defaultExcludes ?: defaultExcludes())
                    }

                    if ( isUpload )
                    {
                        NetworkUtils.upload( resource.targetPaths(), sourceDirectory, includes, excludes, verbose, failIfNotFound )
                        return
                    }
                }
            }

            processFilesResource( resource, sourceDirectory, includes, excludes, verbose, failIfNotFound )
        }
        finally
        {
            /* Sometimes fails with "IllegalArgumentException: wrong number of arguments" */
            if ( isDownload ){ file().delete([ sourceDirectory ] as File[] )}
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
        List<CopyDependency> resourceDependencies = verify().notNullOrEmpty( resource.dependencies())
        final                dependenciesAtM2     = resource.dependenciesAtM2()
        final                isSkipIdentical      = general().choose( resource.skipIdentical, this.skipIdentical )
        final                isStripVersion       = general().choose( resource.stripVersion,  this.stripVersion  )

        if ( dependenciesAtM2 )
        {
            boolean resolved = false // Whether any dependency was resolved

            resolve( resourceDependencies, verbose, failIfNotFound ).each {
                CopyDependency d ->

                resolved = true

                /**
                 * http://evgeny-goldin.org/youtrack/issue/pl-469
                 * File may be resolved from other module "target" (that is built in the same reactor),
                 * not necessarily from ".m2"
                 */
                File f = verify().file( d.artifact.file ).canonicalFile

                (( CopyResource ) resource.clone()).with {

                    directory     = f.parent
                    includes      = [ f.name ]
                    skipIdentical = isSkipIdentical
                    dependencies  = null
                    dependency    = null
                    destFileName  = ( d.destFileName && ( d.destFileName != f.name )) ? d.destFileName : /* the one from <dependench> but not default one, set by Maven */
                                    ( destFileName )                                  ? destFileName   : /* the one from <resource> */
                                                                                        f.name
                    if ( d.stripVersion || isStripVersion )
                    {
                        if ( d.version.endsWith( '-SNAPSHOT' ))
                        {
                            final version    = d.version.substring( 0, d.version.lastIndexOf( '-SNAPSHOT' ))
                            final classifier = d.classifier.with          { delegate ? "-$delegate" : '' }
                            final extension  = file().extension( f ).with { delegate ? ".$delegate" : '' }
                            destFileName     = destFileName.replaceAll( /-\Q$version\E.+?\Q$classifier$extension\E$/,
                                                                        "$classifier$extension" )
                        }
                        else
                        {
                            destFileName = destFileName.replace( "-${ d.version }", '' )
                        }
                    }

                    processFilesResource(( CopyResource ) delegate, verbose, true )
                }
            }

            if ( resolved ) { return }
        }

        final tempDirectory = file().tempDirectory()

        try
        {
            if ( ! dependenciesAtM2 )
            {
                resolve( resourceDependencies, verbose, failIfNotFound, isStripVersion ).each {
                    CopyDependency d -> file().copy( d.artifact.file, tempDirectory, d.destFileName )
                }
            }

            /**
             * Even if zero dependencies were copied to temp (they were all excluded or
             * optional that failed to resolve) we still copy them to the destination due
             * to possible <process> or any other post-processing involved.
             */

            (( CopyResource ) resource.clone()).with {

                directory     = tempDirectory
                includes      = [ '**' ]
                skipIdentical = isSkipIdentical
                dependencies  = null
                dependency    = null

                processFilesResource(( CopyResource ) delegate, verbose, ( tempDirectory.listFiles().size() > 0 ))
            }
        }
        finally
        {
            file().delete(( File ) /* Occasionally fails with "Wrong number of arguments" otherwise */ tempDirectory )
        }
    }


    /**
     * Resolves and filters resource dependencies.
     *
     * @param dependencies   dependencies to resolve and filter
     * @param verbose        whether resolving process should be logged
     * @param failIfNotFound whether execution should fail if zero artifacts were resolved
     * @param stripVersion   whether dependencies version should be stripped
     * @return               dependencies resolved and filtered
     */
    private Collection<CopyDependency> resolve ( List<CopyDependency> dependencies,
                                                 boolean              verbose,
                                                 boolean              failIfNotFound,
                                                 boolean              stripVersion = false )
    {
        assert dependencies

        final result = dependencies.
        collect {
            CopyDependency d ->
            helper.resolveDependencies( d, verbose, failIfNotFound )
        }.
        flatten().
        findAll {
            // Filtering out (optional) unresolved artifacts
            CopyDependency d ->
            d.artifact?.file?.file
        }.
        collect {
            CopyDependency d ->

            d.destFileName = d.destFileName ?:
                             ( d.groupId.startsWith( IVY_PREFIX )) ?
                               /**
                                * Ivy dependencies may carry a fake <classifier> (serving as a pattern to name the artifact)
                                * in "destFileName" which now needs to be removed.
                                */
                                d.artifact.file.name :
                                helper.artifactFileName( d.artifact, ( d.stripVersion || stripVersion ))
            d
        }

        assert ( result || ( ! failIfNotFound ) || dependencies.every { it.optional } ), "No dependencies resolved with [$dependencies]"
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
                verify().directory( sourceDirectory )
            }

            for ( path in resource.targetPaths())
            {
                File targetPath = new File( verify().notNullOrEmpty( path ))

                if ( resource.mkdir )
                {
                    filesToProcess << mkdir( targetPath, verbose )
                }

                if ( resource.pack )
                {
                    final manifestDir = ( manifest.entries ? helper.prepareManifest( manifest ) : null )
                    pack( resource, sourceDirectory, targetPath, includes, excludes, failIfNotFound, manifestDir )?.with{ filesToProcess << delegate }
                    if ( manifestDir ) { file().delete( manifestDir ) }
                }
                else if ( sourceDirectory /* null when mkdir is performed */ )
                {
                    def files = file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound )
                    for ( file in filter( files, resource.filter, verbose, failIfNotFound ))
                    {
                        GCommons.file().mkdirs( targetPath )

                        if ( resource.unpack )
                        {
                            filesToProcess.addAll( unpack( resource, file, targetPath, zipEntries, zipEntriesExclude, verbose, failIfNotFound ))
                        }
                        else
                        {
                            copyResourceFile( resource, sourceDirectory, file, targetPath, verbose )?.with { filesToProcess << delegate }
                        }
                    }
                }
            }
        }

        assert ( resource.startTime > 0 )
        resource.endTime = System.currentTimeMillis()
        process( filesToProcess, resource.process, resource.clean, ( resource.endTime - resource.startTime ))
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

                String extension = file().extension( f )
                String body      = ( extension ? newName.substring( 0, newName.size() - extension.size() - 1 ) : newName )
                extension        = destFileExtension  ?: extension
                newName          = "${ destFilePrefix ?: '' }${ body }${ destFileSuffix ?: '' }${ extension ? '.' + extension : '' }"
            }

            verify().notNullOrEmpty( newName )
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
        assert ! net().isNet( sourceDirectory.path )
        assert ! net().isNet( targetPath.path )

        String  newName  = newName( sourceFile, resource )
        boolean noFilter = split(( resource.nonFilteredExtensions ?: nonFilteredExtensions ?: '' ).toLowerCase()).
                           contains( file().extension( new File( newName )).toLowerCase())
        String  newPath  = resource.preservePath ? file().relativePath( sourceDirectory, new File( sourceFile.parentFile, newName )) : newName
        File    file     = new File( targetPath, newPath )

        assert file.canonicalPath.endsWith( newName )

        copyFile( sourceFile.canonicalFile,
                  file.canonicalFile,
                  general().choose( resource.skipIdentical, skipIdentical ),
                  ( noFilter ? [] : resource.replaces()) as Replace[],
                  (( ! noFilter ) && resource.filtering ),
                  resource.encoding,
                  fileFilter,
                  verbose,
                  resource.move,
                  general().choose( resource.filterWithDollarOnly, filterWithDollarOnly ))
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
            "Directory [$sourceDirectory.canonicalPath] doesn't exist, no files will be packed to [${targetArchive.canonicalPath}]".with {
                assert ( ! failIfNotFound ), delegate
                log.warn(( String ) delegate )
            }

            return null
        }

        final packUsingTemp  = ( resource.replaces() || resource.filtering )
        final filesDirectory = packUsingTemp ? file().tempDirectory() : sourceDirectory
        final skipPacked     = general().choose( resource.skipPacked, this.skipPacked )

        if ( packUsingTemp )
        {
            processFilesResource( resource.makeCopy( this, filesDirectory, sourceDirectory, includes, excludes ),
                                  false, failIfNotFound )
        }

        file().with {

            pack( filesDirectory, targetArchive, includes, excludes,
                  general().choose( resource.useTrueZipForPack, useTrueZipForPack ),
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
            String[] data = split( resource.deploy, '\\|' )

            assert data?.size()?.with{( it == 4 ) || ( it == 5 )}, \
                   "Failed to read <deploy> data [$resource.deploy]. " +
                   'It should be of the following form: "<deployUrl>|<groupId>|<artifactId>|<version>[|<classifier>]"'

            def ( String url, String groupId, String artifactId, String version ) =
                data[ 0 .. 3 ].collect { String s -> verify().notNullOrEmpty( s ) }
            def classifier = (( data.size() == 5 ) ? verify().notNullOrEmpty( data[ 4 ] ) : null )

            helper.deploy( targetArchive, url, groupId, artifactId, version, classifier )
        }

        verify().file( targetArchive )
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
        boolean skipUnpacked = general().choose( resource.skipUnpacked, skipUnpacked )

        if ( skipUnpacked && destinationDirectory.directory && destinationDirectory.listFiles())
        {
            if ( verbose )
            {
                log.info( "<skipUnpacked> is true, directory [$destinationDirectory.canonicalPath] is not empty - " +
                          "unpacking of [$sourceArchive.canonicalPath] was cancelled" )
            }

            return []
        }

        boolean unpackUsingTemp = ( resource.replaces() || resource.filtering )
        File    unpackDirectory = unpackUsingTemp ? file().tempDirectory() : destinationDirectory

        ( zipEntries || zipEntriesExclude ) ?
            file().unpackZipEntries( sourceArchive, unpackDirectory, zipEntries, zipEntriesExclude, resource.preservePath, failIfNotFound ) :
            file().unpack( sourceArchive, unpackDirectory, general().choose( resource.useTrueZipForUnpack, useTrueZipForUnpack ))

        if ( unpackUsingTemp )
        {
            processFilesResource( resource.makeCopy( this, destinationDirectory, unpackDirectory, null, null ), false, true )
            file().delete( unpackDirectory )
        }

        file().files( destinationDirectory )
    }


    /**
     * Creates the directory specified.
     *
     * @param targetPath directory to create
     * @param verbose    verbose logging
     *
     * @return target path created
     */
    private File mkdir( File    targetPath,
                        boolean verbose )
    {
        if ( targetPath.directory )
        {
            if ( verbose ){ log.info( "Directory [$targetPath.canonicalPath] already exists" )}
            return targetPath
        }

        file().mkdirs( targetPath )
        if ( verbose ){ log.info( "Directory [$targetPath.canonicalPath] created" )}

        verify().directory( targetPath )
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
            "Directory [$sourceDirectory.canonicalPath] doesn't exist, no files will be deleted".with {
                assert ( ! failIfNotFound ), delegate
                log.warn(( String ) delegate )
            }

            return []
        }

        List<File> files        = file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound )
        List<File> filesDeleted = filter( files, filterExpression, verbose, failIfNotFound )

        file().delete( filesDeleted as File[] )

        if ( cleanEmptyDirectories )
        {
            List<File> directoriesDeleted = ( file().recurse( sourceDirectory, [ type : FileType.DIRECTORIES ], {} ) + sourceDirectory ).
                                            findAll{ File f -> assert f.directory
                                                               file().directorySize( f ) == 0 } /* Not all files could be deleted */
            file().delete( directoriesDeleted as File[] )
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
    private List<File> filter( List<File> files,
                               String     filterExpression,
                               boolean    verbose,
                               boolean    failIfNotFound )
    {
        if  ( filterExpression )
        {
            verify().file( files as File[] )

            String           expression    = verify().notNullOrEmpty( FILTERS[ filterExpression ] ?: filterExpression )
            Object           o             = eval( expression, Object, groovyConfig, 'files', files, 'file', files ? files.first() : null )
            Collection<File> filesIncluded = (( o instanceof File       ) ? [ ( File ) o ]            :
                                              ( o instanceof Collection ) ? (( Collection<File> ) o ) :
                                                                            null )
            assert ( filesIncluded != null ), \
                   "Executing Groovy expression [$expression] produced [$o] of type [${ o.class.name }]. " +
                   'It should be an instance of File or Collection<File>.'

            if ( verbose )
            {
                log.info( "Files left after applying <filter>:${ constants().CRLF }${ stars( filesIncluded ) }" )
            }

            return filesIncluded as List
        }

        assert (( files ) || ( ! failIfNotFound )) // If not files are found - it should be handled by GCommons already
        files
    }


    /**
     * Processes files provided using Groovy expression specified.
     *
     * @param                   files to process
     * @param processExpression Groovy expression, if <code>null</code> - no processing is executed
     * @param verbose           whether logging should be verbose
     * @param isClean           whether files processed are of <clean> operation
     */
    @Requires({ ! (( files == null ) || files.any{ it == null } ) })
    private void process( List<File> files, String processExpression, boolean isClean, long time )
    {
        assert ( time >= 0 )

        if ( processExpression )
        {   /**
             * There may be no files to process if all of them were skipped due to "skipIdentical"
             */
            Set<File> filesSet = files as Set
            assert    filesSet.size() <= files.size()

            // noinspection GroovyAssignmentToMethodParameter
            files = ( filesSet.size() < files.size() ? filesSet as List /* Duplicates found */ : files )
            if ( ! isClean ){ verify().file( files as File[] )}

            eval( processExpression, null, groovyConfig, 'files', files, 'file', files ? files.first() : null, 'time', time )
        }
    }
}
