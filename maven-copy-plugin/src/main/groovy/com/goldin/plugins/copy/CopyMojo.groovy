package com.goldin.plugins.copy

import com.goldin.gcommons.util.GroovyConfig
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
import static com.goldin.plugins.common.GMojoUtils.*
import org.jfrog.maven.annomojo.annotations.*

 /**
 * MOJO copying resources specified
 */
@MojoGoal( 'copy' )
@MojoPhase( 'package' )
@MojoRequiresDependencyResolution( 'test' )
class CopyMojo extends org.apache.maven.plugin.dependency.fromConfiguration.CopyMojo
{
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

    @MojoParameter ( required = false )
    public GroovyConfig groovyConfig

    @MojoParameter ( required = false )
    public boolean skipIdentical = false

    /**
     * "false" or comma-separated list of default excludes
     * Not active for Net operations
     */
    @MojoParameter ( required = false )
    public String  defaultExcludes =
        ( [ '**/.settings/**', '**/.classpath', '**/.project', '**/*.iws', '**/*.iml', '**/*.ipr' ] +
          FileUtils.defaultExcludes as List ).join( ',' )

    @MojoParameter ( required = false )
    public  boolean verbose = true

    @MojoParameter ( required = false )
    public  boolean failIfNotFound = true

    @MojoParameter ( required = false )
    public  boolean update = false

    @MojoParameter ( required = false )
    public  CopyResource[] resources

    @MojoParameter ( required = false )
    public  CopyResource resource

    private CopyResource[] resources () { generalBean().array( this.resources, this.resource, CopyResource ) }


    @MojoParameter
    public  String  runIf


    /**
     * Predefined {@code <filter>} values:
     * Key   - {@code <filter>} value
     * Value - Groovy expression to use
     */
    private static final Map<String, String> FILTERS = [ '{{latest}}' :
        """
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
        file""" ]


    public CopyMojo ()
    {
    }


    /**
     * Copies the Resources specified
     */
    @Override
    public void execute() throws MojoExecutionException
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

        verifyBean().isNull( this.project, this.factory, this.resolver, this.local, this.remoteRepos )
        this.project     = mavenProject
        this.factory     = artifactFactory
        this.resolver    = artifactResolver
        this.local       = artifactRepository
        this.remoteRepos = remoteArtifactRepositories
        def eval         = { String s -> s = s.trim()
                                         (( s.startsWith( '{{' )) && ( s.endsWith( '}}' ))) ? groovy( s, String ) : s }

        for ( CopyResource resource in resources())
        {
            if ( resource.description ) { log.info( "==> Processing <resource> [${ eval( resource.description )}]" )}
            if ( ! runIf( resource.runIf )) { continue }

            long    t               = System.currentTimeMillis()
            boolean verbose         = generalBean().choose( resource.verbose,        verbose        )
            boolean failIfNotFound  = generalBean().choose( resource.failIfNotFound, failIfNotFound )
            boolean resourceHandled = false

            resource.includes = update( resource.includes, resource.encoding )
            resource.excludes = update( resource.excludes, resource.encoding )

            if ( resource.mkdir || resource.directory )
            {
                handleResource( resource, verbose, failIfNotFound )
                resourceHandled = true
            }

            if ( resource.dependencies())
            {
                handleDependencies( resource, verbose )
                resourceHandled = true
            }

            assert resourceHandled, "Couldn't handle <resource> [$resource] - is it configured properly?"
            if ( resource.description )
            {
                log.info( "==> <resource> [${ eval( resource.description )}] processed, [${ System.currentTimeMillis() - t }] ms" )
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

        def  isDownload        = netBean().isNet( resource.directory )
        def  isUpload          = netBean().isNet( resource.targetPaths())
        File sourceDirectory   = ( resource.mkdir ? null                            : // Only <targetPath> is active
                                   isDownload     ? fileBean().tempDirectory()      : // Temp dir to download the files to
                                                    new File( resource.directory ))   // Directory to cleanup, upload or copy

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
                if  (( resource.defaultExcludes != 'false' ) && ( defaultExcludes != 'false' ))
                {
                    excludes = ( excludes ?: [] ) +
                               ( resource.defaultExcludes ?: defaultExcludes ).split( ',' )*.trim().findAll{ it }
                }

                if ( isUpload )
                {
                    NetworkUtils.upload( resource.targetPaths(), sourceDirectory, includes, excludes, verbose, failIfNotFound )
                    return
                }
            }
        }

        handleResource( resource, sourceDirectory, includes, excludes, verbose, failIfNotFound )

        if ( isDownload ){ fileBean().delete( sourceDirectory )}
    }


    /**
     * Handles resource {@code <dependencies>}.
     *
     * @param resource resource to handle
     * @param verbose  verbose logging
     */
    private void handleDependencies ( CopyResource resource, boolean verbose )
    {
        List<CopyDependency> dependencies = resource.dependencies() as List
        verifyBean().notNullOrEmpty( dependencies )

        if ( resource.dependenciesAtM2 )
        {
            resolveDependencies( dependencies ).each {
                CopyDependency d ->
                File m2File = verifyBean().file( d.artifact.file )
                handleResource( resource, m2File.parentFile, [ m2File.name ], null, verbose, true )
            }
        }
        else
        {
            File tempDirectory      = fileBean().tempDirectory()
            int  dependenciesCopied = 0

            resolveDependencies( dependencies, tempDirectory, resource.stripVersion ).each {
                CopyDependency d ->
                copyArtifact( d ) // Copy <dependency> to temp directory
                dependenciesCopied++
            }

            // Zero dependencies can be copied if some of them are optional and are not resolved.
            handleResource( resource, tempDirectory, null, null, verbose, ( dependenciesCopied > 0 ))
            fileBean().delete( tempDirectory )
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
    private List<CopyDependency> resolveDependencies ( List<CopyDependency> dependencies,
                                                       File                 outputDirectory = constantsBean().USER_DIR_FILE,
                                                       boolean              stripVersion    = false )
    {
        verifyBean().notNullOrEmpty( dependencies )
        verifyBean().directory( outputDirectory )

        List<CopyDependency> dependenciesResolved = ( List<CopyDependency> ) dependencies.inject( [] ) {
            List<CopyDependency> list, CopyDependency d ->
            list.addAll( d.groupId ? [ d ] : CopyMojoUtils.getFilteredDependencies( d ))
            list
        }.collect {
            CopyDependency d ->

            verifyBean().notNullOrEmpty( d.groupId, d.artifactId )

            try
            {
                d.outputDirectory = outputDirectory
                setArtifactItems(( ArrayList ) [ d ] )
                ( CopyDependency ) ( getProcessedArtifactItems( stripVersion )[ 0 ] )
            }
            catch ( Exception e )
            {
                if ( d.optional )
                {
                    log.warn( "Failed to resolve optional dependency [$d]: $e" )
                }
                else
                {
                    throw new RuntimeException( "Failed to resolve dependency [$d]: $e", e )
                }
            }
        }.findAll{ it } // Filtering out nulls that can be resulted by optional dependencies that failed to be resolved

        dependenciesResolved
    }


    /**
     * Analyzes patterns specified and updates them if required:
     * - if any of them is comma or space-separated, splits it to additional patterns
     * - if any of them starts with "file:" or "classpath:", each line in the resource
     *   loaded is converted to a pattern
     *
     * @param patterns patterns to analyze
     * @param files    encoding
     *
     * @return updated patterns list
     */
    private List<String> update( List<String> patterns, String encoding )
    {
        if ( ! patterns ) { return patterns }

        List<String> newPatterns = []

        for ( pattern in patterns*.trim().findAll { it })
        {
            newPatterns.addAll(
                pattern.startsWith( 'file:'      ) ? new File( pattern.substring( 'file:'.length())).getText( encoding ).readLines()            :
                pattern.startsWith( 'classpath:' ) ? CopyMojo.class.getResourceAsStream( pattern.substring( 'classpath:'.length())).readLines() :
                pattern.contains( ',' )            ? pattern.split( ',' )*.trim().findAll{ it }                                                 :
                                                     [ pattern ] )
        }

        newPatterns*.trim()
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
    private CopyResource handleResource ( CopyResource resource,
                                          File         sourceDirectory,
                                          List<String> includes        = null,
                                          List<String> excludes        = null,
                                          boolean      verbose         = true,
                                          boolean      failIfNotFound  = true )
    {
        verifyBean().notNull( resource )
        def zipEntries = resource.zipEntries() as List

        if ( zipEntries )
        {
            assert resource.unpack, 'You should specify <unpack>true</unpack> when using <zipEntry> or <zipEntries>'
        }

        if ( resource.clean )
        {
            clean( sourceDirectory, includes, excludes, resource.cleanEmptyDirectories, resource.filter, verbose, failIfNotFound )
            return resource
        }

        if ( ! resource.mkdir )
        {
            verifyBean().directory( sourceDirectory )
        }

        def filesToProcess = []

        for ( path in resource.targetPaths())
        {
            File targetPath = new File( verifyBean().notNullOrEmpty( path ))

            if ( resource.mkdir )
            {
                filesToProcess << mkdir( targetPath, verbose )
            }
            else if ( resource.pack )
            {
                filesToProcess << pack( resource, sourceDirectory, targetPath, includes, excludes, failIfNotFound )
            }
            else
            {   /**
                 * Taking each <include> separately to make sure it doesn't come out empty
                 * (if we take all <includes> at once and some patterns come empty - no exception will be thrown)
                 */
                def files = []
                if ( includes ) { includes.each{ files.addAll( fileBean().files( sourceDirectory, [ it ], excludes, true, false, failIfNotFound )) }}
                else            {                files.addAll( fileBean().files( sourceDirectory, null,   excludes, true, false, failIfNotFound ))  }

                for ( file in filter( files, resource.filter, verbose, failIfNotFound ))
                {
                    if ( resource.unpack )
                    {
                        zipEntries ? fileBean().unpackZipEntries( file, targetPath, zipEntries, resource.preservePath, verbose ) :
                                     fileBean().unpack( file, targetPath )
                        filesToProcess << file
                    }
                    else
                    {
                        File fileCopied = copy( resource, sourceDirectory, file, targetPath, verbose )
                        if ( fileCopied )
                        {
                            filesToProcess << fileCopied
                        }
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
     * Copies the file specified.
     *
     * @param resource        current copy resource
     * @param sourceDirectory file base directory
     * @param file            file to copy
     * @param targetPath      target location to copy the file to
     * @param verbose         verbose logging
     * @return file copied if copying was performed, null otherwise
     */
    private File copy( CopyResource resource,
                       File         sourceDirectory,
                       File         file,
                       File         targetPath,
                       boolean      verbose )
    {
        assert ! netBean().isNet( sourceDirectory.path )
        assert ! netBean().isNet( targetPath.path )

        boolean skipIdentical = (( ! resource.process ) && /* If file is processed - it is not skipped */
                                 generalBean().choose( resource.skipIdentical, skipIdentical ))
        /**
         * Location where the file will be copied to
         */
        String filePath = new File( targetPath, resource.preservePath ? relativePath( sourceDirectory, file ) :
                                                                        file.name ).canonicalPath
        assert filePath.endsWith( file.name )

        if ( resource.destFileName )
        {
            filePath = filePath.substring( 0, filePath.lastIndexOf( file.name )) + resource.destFileName
        }

        File    targetFile = new File( filePath )
        boolean copied     = copy( file,
                                              targetFile,
                                              skipIdentical,
                                              resource.replaces(),
                                              resource.filtering,
                                              resource.encoding,
                                              fileFilter,
                                              verbose )

        copied ? verifyBean().file( targetFile ) : null
    }


    /**
     * Retrieves relative path of file inside directory specified.
     * For example: for directory <code>"C:\some"</code> and child file <code>"C:\some\folder\opa\1.txt"</code>
     * this function returns <code>"\folder\opa\1.txt"</code>.
     *
     * @param directory file's parent directory
     * @param file      directory's child file
     * @return          relative path of file inside directory specified, starts with "\" or "/"
     */
    private static String relativePath( File directory, File file )
    {
        verifyBean().notNull( directory, file )

        String directoryPath = directory.canonicalPath
        String filePath      = file.canonicalPath

        assert filePath.startsWith( directoryPath ), \
               "File [$filePath] is not a child of [$directoryPath]"


        String relativePath = verifyBean().notNullOrEmpty( filePath.substring( directoryPath.length()))
        assert ( relativePath.startsWith( "/" ) || relativePath.startsWith( "\\" ))

        relativePath
    }


    /**
     * Packs directory specified.
     *
     * @param resource        current copy resource
     * @param sourceDirectory directory to pack
     * @param targetPath      target archie to pack the directory to
     * @param includes        files to include, may be <code>null</code>
     * @param excludes        files to exclude, may be <code>null</code>
     * @param failIfNotFound  fail if directory not found or no files were included
     *
     * @return target archive packed
     */
    private File pack( CopyResource resource,
                       File         sourceDirectory,
                       File         targetPath,
                       List<String> includes,
                       List<String> excludes,
                       boolean      failIfNotFound )
    {
        fileBean().pack( sourceDirectory, targetPath, includes, excludes, true, failIfNotFound, resource.update )

        if ( resource.attachArtifact )
        {
            mavenProjectHelper.attachArtifact( mavenProject,
                                               fileBean().extension( targetPath ),
                                               resource.artifactClassifier,
                                               targetPath )
        }

        if ( resource.deploy )
        {
            String[] data = resource.deploy.split( /\|/ )

            assert ( data?.size()?.any{( it == 3 ) || ( it == 4 )}), \
                   "Failed to split <deploy> tag data [$resource.deploy]. " +
                   'It should be of the following form: "<deployUrl>|<groupId>|<artifactId>|<version>[|<classifier>]"'

            def ( String url, String groupId, String artifactId, String version ) = data[ 0 .. 3 ].collect { String s -> verifyBean().notNullOrEmpty( s ) }
            def classifier = (( data.size() == 4 ) ? verifyBean().notNullOrEmpty( data[ 4 ] ) : null )

            deploy( targetPath, url, groupId, artifactId, version, classifier, pluginManager )
        }

        verifyBean().file( targetPath )
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
        if ( targetPath.isDirectory())
        {
            if ( verbose ){ log.info( "Directory [$targetPath.canonicalPath] already exists" )}
            return
        }

        fileBean().mkdirs( targetPath )
        if ( verbose ){ log.info( "Directory [$targetPath.canonicalPath] created" )}

        verifyBean().directory( targetPath )
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
        if ( failIfNotFound ) { verifyBean().directory( sourceDirectory ) }

        if ( sourceDirectory.isDirectory())
        {
            def filesDeleted = filter( fileBean().files( sourceDirectory, includes, excludes, true, false, failIfNotFound ),
                                       filterExpression, verbose, failIfNotFound )

            fileBean().delete( filesDeleted as File[] )

            if ( cleanEmptyDirectories )
            {
                List<File> directoriesDeleted = ( sourceDirectory.splitWith( 'eachDirRecurse', File ) + sourceDirectory ).
                                                findAll{ File f -> f.isDirectory() && ( f.directorySize() == 0 )}

                fileBean().delete( directoriesDeleted as File[] )
                filesDeleted += directoriesDeleted
            }

            if ( verbose ) { log.info( "[$sourceDirectory.canonicalPath] files deleted: $filesDeleted" )}
        }
        else if ( verbose )
        {
            assert ( ! sourceDirectory.isDirectory()) && ( ! failIfNotFound )
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
    private List<File> filter( List<File> files,
                               String     filterExpression,
                               boolean    verbose,
                               boolean    failIfNotFound )
    {
        if  (( ! failIfNotFound ) && ( ! files ))
        {
            // Do not "failIfNotFound" and no files found
            return files
        }

        verifyBean().exists( files as File[] )

        if ( ! filterExpression )
        {
            return files
        }

        String           expression    = verifyBean().notNullOrEmpty( FILTERS[ filterExpression ] ?: filterExpression )
        Object           o             = groovy( expression, Object, groovyConfig, 'files', files )
        Collection<File> filesIncluded = (( o instanceof File       ) ? [ ( File ) o ]            :
                                          ( o instanceof Collection ) ? (( Collection<File> ) o ) :
                                                                        null )
        assert ( filesIncluded != null ), \
               "Executing Groovy expression [$expression] produced [$o] of type [${ o.class.name }]. " +
               "It should be an instance of File or Collection<File>."

        if ( verbose )
        {
            log.info( "Files left after applying <filter>:${ constantsBean().CRLF }${ stars( filesIncluded ) }" )
        }

        filesIncluded
    }


    /**
     * Processes files provided using Groovy expression specified.
     *
     * @param                   files to process
     * @param processExpression Groovy expression, if <code>null</code> - no processing is executed
     * @param verbose           whether logging should be verbose
     */
    private void process( List<File> files, String processExpression )
    {
        if ( ! files )
        {   /**
             * There may be no files to process if all of them were skipped
             * ("skipIdentical" calculated to "true" for specific <resource>)
             */
            return
        }

        verifyBean().exists( files as File[] )

        if ( processExpression )
        {
            groovy( processExpression, null, groovyConfig, 'files', files )
        }
    }
}
