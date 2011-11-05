package com.goldin.plugins.copy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.gcommons.util.GroovyConfig
import com.goldin.plugins.common.GMojoUtils
import com.goldin.plugins.common.Replace
import com.goldin.plugins.common.ThreadLocals
import org.apache.maven.artifact.factory.ArtifactFactory
import org.apache.maven.artifact.metadata.ArtifactMetadataSource
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.PluginManager
import org.apache.maven.project.MavenProject
import org.apache.maven.project.MavenProjectHelper
import org.apache.maven.shared.filtering.MavenFileFilter
import org.codehaus.plexus.util.FileUtils
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.*


/**
 * MOJO copying resources specified
 */
@MojoGoal( 'copy' )
@MojoPhase( 'package' )
@MojoRequiresDependencyResolution( 'test' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class CopyMojo extends org.apache.maven.plugin.dependency.fromConfiguration.CopyMojo
{
    /**
     * Container-injected fields
     */

    @MojoComponent
    public ArtifactFactory artifactFactory

    @MojoComponent
    public PluginManager pluginManager

    @MojoComponent
    public ArtifactResolver artifactResolver

    @MojoComponent
    public ArtifactMetadataSource metadataSource

    @MojoComponent
    public MavenProjectHelper mavenProjectHelper

    @MojoComponent ( role = 'org.apache.maven.shared.filtering.MavenFileFilter', roleHint = 'default' )
    public MavenFileFilter fileFilter

    @MojoParameter( expression = '${project}' )
    public MavenProject mavenProject

    @MojoParameter ( expression = '${session}', readonly = true, required = true )
    public MavenSession mavenSession

    @MojoParameter ( expression = '${localRepository}', readonly = true, required = true )
    public  ArtifactRepository artifactRepository

    @MojoParameter ( expression = '${project.remoteArtifactRepositories}', readonly = true, required = true )
    public  List<ArtifactRepository> remoteArtifactRepositories

    /**
     * User-provided fields
     */

    @MojoParameter
    public  String  runIf

    @MojoParameter ( required = false )
    public boolean skipIdentical = false

    @MojoParameter ( required = false )
    public boolean skipUnpacked = false

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
    public  boolean useTrueZipForPack = false

    @MojoParameter ( required = false )
    public  boolean useTrueZipForUnpack = true

    @MojoParameter ( required = false )
    public  CopyResource[] resources

    @MojoParameter ( required = false )
    public  CopyResource resource

    private CopyResource[] resources () { general().array( this.resources, this.resource, CopyResource ) }

    @MojoParameter ( required = false )
    public GroovyConfig groovyConfig


    private final CopyMojoHelper helper = new CopyMojoHelper()


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
    @SuppressWarnings( 'AbcComplexity' )
    void execute() throws MojoExecutionException
    {
        /**
         * See {@link com.goldin.plugins.common.BaseGroovyMojo#execute()} - we duplicate
         * it here as long as we can't extend it
         */
        ThreadLocals.set( log, mavenProject, mavenSession, artifactFactory, artifactResolver, metadataSource )
        mopInit()

        if ( ! runIf( runIf )) { return }

        /**
         * Initializing super-class fields
         * (they were not initialized since Maven doesn't set annotated parameters in super-class).
         *
         * Next verifications below will break when this bug is fixed in Maven
         */

        verify().isNull( this.project, this.factory, this.resolver, this.local, this.remoteRepos )
        this.project     = mavenProject
        this.factory     = artifactFactory
        this.resolver    = artifactResolver
        this.local       = artifactRepository
        this.remoteRepos = remoteArtifactRepositories
        def groovyEval   = { String s -> s.trim().with{ ( startsWith( '{{' ) && endsWith( '}}' )) ? eval( delegate, String ) : delegate }}

        for ( CopyResource resource in resources())
        {
            if ( resource.description ) { log.info( "==> Processing <resource> [${ groovyEval( resource.description )}]" )}
            if ( runIf( resource.runIf ))
            {
                long    t               = System.currentTimeMillis()
                boolean verbose         = general().choose( resource.verbose,        verbose        )
                boolean failIfNotFound  = general().choose( resource.failIfNotFound, failIfNotFound )
                boolean resourceHandled = false
                resource.directory      = helper.canonicalPath ( resource.directory )
                resource.includes       = helper.updatePatterns( resource.directory, resource.includes, resource.encoding )
                resource.excludes       = helper.updatePatterns( resource.directory, resource.excludes, resource.encoding )

                if ( resource.mkdir || resource.directory )
                {
                    handleResource( resource, verbose, failIfNotFound )
                    resourceHandled = true
                }

                if ( resource.dependencies())
                {
                    handleDependencies( resource, verbose, failIfNotFound )
                    resourceHandled = true
                }

                assert resourceHandled, "Couldn't handle <resource> [$resource] - is it configured properly?"
                if ( resource.description )
                {
                    log.info( "==> <resource> [${ groovyEval( resource.description )}] processed, [${ System.currentTimeMillis() - t }] ms" )
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
    private void handleResource ( CopyResource resource, boolean verbose, boolean failIfNotFound )
    {
        assert ( resource.mkdir || resource.directory )

        def  isDownload      = net().isNet( resource.directory )
        def  isUpload        = net().isNet( resource.targetPaths())
        File sourceDirectory = ( isDownload         ? file().tempDirectory()     : // Temp dir to download the files to
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

            handleResource( resource, sourceDirectory, includes, excludes, verbose, failIfNotFound )
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
     * @param resource resource to handle
     * @param verbose  verbose logging
     */
    private void handleDependencies ( CopyResource resource, boolean verbose, boolean failIfNotFound )
    {
        List<CopyDependency> dependencies = resource.dependencies() as List
        verify().notNullOrEmpty( dependencies )

        if ( resource.dependenciesAtM2())
        {
            resolveDependencies( dependencies ).each {
                CopyDependency d ->
                /**
                 * http://evgeny-goldin.org/youtrack/issue/pl-469
                 * This dependency may be resolved from other module "target", not necessarily from ".m2"
                 */
                File file = verify().file( d.artifact.file ).canonicalFile

                (( CopyResource ) resource.clone()).with {

                    directory    = file.parent
                    includes     = [ file.name ]
                    dependencies = null
                    dependency   = null

                    if ( d.destFileName && ( d.destFileName != file.name ))
                    {
                        destFileName = d.destFileName
                    }

                    handleResource(( CopyResource ) delegate, verbose, failIfNotFound )
                }
            }
        }
        else
        {
            File tempDirectory = file().tempDirectory()

            try
            {
                int  dependenciesCopied = 0
                resolveDependencies( dependencies, tempDirectory, resource.stripVersion ).each {
                    CopyDependency d ->
                    copyArtifact( d )    // Copies <dependency> to temp directory
                    dependenciesCopied++ // Zero dependencies can be copied if some of them are optional and can't be resolved.
                }

                if ( dependenciesCopied > 0 )
                {
                    (( CopyResource ) resource.clone()).with {

                        directory    = tempDirectory
                        includes     = [ '**' ]
                        dependencies = null
                        dependency   = null

                        handleResource(( CopyResource ) delegate, verbose, failIfNotFound )
                    }
                }
            }
            finally
            {
                file().delete(( File ) /* Fails with "wrong number of arguments" sometimes */ tempDirectory )
            }
        }
    }


    /**
     * Resolves and filters resource dependencies.
     *
     * @param dependencies    dependencies to resolve and filter
     * @param outputDirectory dependencies output directory to set
     * @param stripVersion    whether dependencies version should be stripped
     * @return                dependencies resolved and filtered
     */
    private Collection<CopyDependency> resolveDependencies ( List<CopyDependency> dependencies,
                                                             File                 outputDirectory = constants().USER_DIR_FILE,
                                                             boolean              stripVersion    = false )
    {
        assert dependencies && outputDirectory.directory

        ( Collection<CopyDependency> ) dependencies.
        collect {
            CopyDependency d -> helper.getDependencies( d ) }.
        flatten().
        collect {
            CopyDependency d ->

            verify().notNullOrEmpty( d.groupId, d.artifactId )

            try
            {
                d.outputDirectory = outputDirectory
                setArtifactItems([ d ])
                ( CopyDependency ) ( getProcessedArtifactItems( stripVersion )[ 0 ] )
            }
            catch ( MojoExecutionException e )
            {
                if ( d.optional )
                {
                    log.warn( "Failed to resolve optional dependency [$d]: $e" )
                }
                else
                {
                    throw new MojoExecutionException( "Failed to resolve dependency [$d]", e )
                }
            }
        }.
        grep() // Filtering out nulls that can be resulted by optional dependencies that failed to be resolved
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
    private CopyResource handleResource ( CopyResource resource,
                                          File         sourceDirectory,
                                          List<String> includes        = null,
                                          List<String> excludes        = null,
                                          boolean      verbose         = true,
                                          boolean      failIfNotFound  = true )
    {
        List<String> zipEntries        = resource.zipEntries()        as List
        List<String> zipEntriesExclude = resource.zipEntriesExclude() as List

        if ( zipEntries        ) { assert resource.unpack, '<zipEntry> or <zipEntries> can only be used with <unpack>true</unpack>' }
        if ( zipEntriesExclude ) { assert resource.unpack, '<zipEntryExclude> or <zipEntriesExclude> can only be used with <unpack>true</unpack>' }
        if ( resource.prefix   ) { assert resource.pack,   '<prefix> can only be used with <pack>true</pack>' }

        if ( resource.clean  )
        {
            clean( sourceDirectory, includes, excludes, resource.cleanEmptyDirectories, resource.filter, verbose, failIfNotFound )
            return resource
        }

        if ( ! resource.mkdir )
        {
            verify().directory( sourceDirectory )
        }

        def filesToProcess = []

        for ( path in resource.targetPaths())
        {
            File targetPath = new File( verify().notNullOrEmpty( path ))

            if ( resource.mkdir )
            {
                filesToProcess << mkdir( targetPath, verbose )
            }

            if ( resource.pack )
            {
                filesToProcess << pack( resource, sourceDirectory, targetPath, includes, excludes, failIfNotFound )
            }
            else if ( sourceDirectory /* null when mkdir is performed */ )
            {
                def files = file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound )
                for ( file in filter( files, resource.filter, verbose, failIfNotFound ))
                {
                    if ( resource.unpack )
                    {
                        filesToProcess.addAll( unpack( resource, file, targetPath, zipEntries, zipEntriesExclude, verbose, failIfNotFound ))
                    }
                    else
                    {
                        File fileCopied = copyResourceFile( resource, sourceDirectory, file, targetPath, verbose )
                        if ( fileCopied ) { filesToProcess << fileCopied }
                    }
                }
            }
        }

        if ( ! resource.clean )
        {
            process( filesToProcess, resource.process )
        }

        resource
    }


    /**
     * Calculates new name of the file to copy taking its resource into consideration.
     *
     * @param file     file to copy
     * @param resource file's <resource>
     * @return         file's new name
     */
    @Requires({ file && resource })
    @Ensures({ result })
    private String newName ( File file, CopyResource resource )
    {
        resource.with {

            if ( destFileName ) { return destFileName }

            String newName = file.name

            if ( destFilePrefix || destFileSuffix || destFileExtension )
            {
                assert ( targetPaths() && directory && ( ! ( pack || unpack ))), \
                       '<destFilePrefix>, <destFileSuffix>, <destFileExtension> can only be used when files are copied from <targetPath> to <directory>'

                String extension = GMojoUtils.file().extension( file )
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
    @Requires({ resource && sourceDirectory && sourceFile && targetPath })
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
                  ( noFilter ? [] as Replace[] : resource.replaces()),
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
     *
     * @return target archive packed
     */
    @SuppressWarnings( 'AbcComplexity' )
    private File pack( CopyResource resource,
                       File         sourceDirectory,
                       File         targetArchive,
                       List<String> includes,
                       List<String> excludes,
                       boolean      failIfNotFound )
    {
        boolean packUsingTemp  = ( resource.replaces() || resource.filtering )
        File    filesDirectory = packUsingTemp ? file().tempDirectory() : sourceDirectory

        if ( packUsingTemp )
        {
            handleResource( resource.makeCopy( this, filesDirectory, sourceDirectory, includes, excludes ),
                            false, failIfNotFound )
        }

        file().pack( filesDirectory, targetArchive, includes, excludes,
                     general().choose( resource.useTrueZipForPack, useTrueZipForPack ),
                     failIfNotFound, resource.update,
                     split( resource.defaultExcludes ?: defaultExcludes()),
                     resource.destFileName, resource.prefix )

        if ( resource.move ) { file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound ).
                                      each { file().delete( it ) }}

        if ( packUsingTemp ) { file().delete( filesDirectory ) }

        if ( resource.attachArtifact )
        {
            mavenProjectHelper.attachArtifact( mavenProject,
                                               file().extension( targetArchive ),
                                               resource.artifactClassifier,
                                               targetArchive )
        }

        if ( resource.deploy )
        {
            String[] data = split( resource.deploy, '\\|' )

            assert data?.size()?.with{( it == 3 ) || ( it == 4 )}, \
                   "Failed to split <deploy> tag data [$resource.deploy]. " +
                   'It should be of the following form: "<deployUrl>|<groupId>|<artifactId>|<version>[|<classifier>]"'

            def ( String url, String groupId, String artifactId, String version ) =
                data[ 0 .. 3 ].collect { String s -> verify().notNullOrEmpty( s ) }
            def classifier = (( data.size() == 4 ) ? verify().notNullOrEmpty( data[ 4 ] ) : null )

            deploy( targetArchive, url, groupId, artifactId, version, classifier, pluginManager )
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
     * @return
     */
    @Requires({ resource && sourceArchive && destinationDirectory && ( zipEntries != null ) && ( zipEntriesExclude != null ) })
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
            handleResource( resource.makeCopy( this, destinationDirectory, unpackDirectory, null, null ), false, true )
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
    private void clean( File         sourceDirectory,
                        List<String> includes,
                        List<String> excludes,
                        boolean      cleanEmptyDirectories,
                        String       filterExpression,
                        boolean      verbose,
                        boolean      failIfNotFound )
    {
        if ( failIfNotFound ) { verify().directory( sourceDirectory ) }

        if ( sourceDirectory.directory )
        {
            def filesDeleted = filter( file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound ),
                                       filterExpression, verbose, failIfNotFound )

            file().delete( filesDeleted as File[] )

            if ( cleanEmptyDirectories )
            {
                List<File> directoriesDeleted = ( sourceDirectory.splitWith( 'eachDirRecurse', File ) + sourceDirectory ).
                                                findAll{ File f -> f.directory && ( f.directorySize() == 0 )}

                file().delete( directoriesDeleted as File[] )
                filesDeleted += directoriesDeleted
            }

            if ( verbose ) { log.info( "[$sourceDirectory.canonicalPath] files deleted: $filesDeleted" )}
        }
        else if ( verbose )
        {
            assert ( ! sourceDirectory.directory ) && ( ! failIfNotFound )
            log.info( "[$sourceDirectory.canonicalPath] doesn't exist, <failIfNotFound> is [$failIfNotFound], no files deleted" )
        }
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
     */
    @Requires({ files != null })
    private void process( List<File> files, String processExpression )
    {
        if ( processExpression )
        {   /**
             * There may be no files to process if all of them were skipped due to "skipIdentical"
             */
            Set<File> filesSet = files as Set
            assert    filesSet.size() <= files.size()

            // noinspection GroovyAssignmentToMethodParameter
            files = ( filesSet.size() < files.size() ? filesSet as List /* Duplicates found */ : files )
            verify().file( files as File[] )

            eval( processExpression, null, groovyConfig, 'files', files, 'file', files ? files.first() : null )
        }
    }
}
