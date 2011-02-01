package com.goldin.plugins.copy

import com.goldin.gcommons.GCommons
import com.goldin.gcommons.util.GroovyConfig
import com.goldin.plugins.common.GMojoUtils
import com.goldin.plugins.common.MojoUtils
import com.goldin.plugins.common.NetworkUtils
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
import org.jfrog.maven.annomojo.annotations.*

 /**
 * MOJO copying resources specified
 */
@MojoGoal( 'copy' )
@MojoPhase( 'package' )
@MojoRequiresDependencyResolution( 'test' )
class CopyMojo extends org.apache.maven.plugin.dependency.fromConfiguration.CopyMojo
{
    static { GMojoUtils.init() }

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
    public  CopyResource[] resources

    @MojoParameter ( required = false )
    public  CopyResource resource

    private CopyResource[] resources () { GCommons.general().array( this.resources, this.resource, CopyResource ) }

    @MojoParameter ( expression = '${localRepository}', readonly = true, required = true )
    public  ArtifactRepository artifactRepository

    @MojoParameter ( expression = '${project.remoteArtifactRepositories}', readonly = true, required = true )
    public  List<ArtifactRepository> remoteArtifactRepositories

    @MojoParameter
    public  String  runIf


    /**
     * Predefined {@code <filter>} values:
     * Key   - {@code <filter>} value
     * Value - Groovy expression to use
     */
    private static final Map<String, String> FILTERS = Collections.unmodifiableMap( new HashMap<String, String>()
    {{
         put( "{{latest}}", "{{ files.sort{ a, b -> (( a.lastModified() > b.lastModified()) ? -1 : 1 ) }.first() }}" )
    }})


    public CopyMojo ()
    {
    }


    /**
     * Copies the Resources specified
     */
    @Override
    public void execute() throws MojoExecutionException
    {
        ThreadLocals.set( log, mavenProject, mavenSession, artifactFactory, artifactResolver, metadataSource )
        if ( ! GMojoUtils.runIf( runIf )) { return }

        /**
         * Initializing super-class fields
         * (they were not initialized since Maven doesn't set annotated parameters in super-class).
         *
         * Next verifications below will break when this bug is fixed in Maven
         */

        GCommons.verify().isNull( this.project, this.factory, this.resolver, this.local, this.remoteRepos )
        this.project     = mavenProject
        this.factory     = artifactFactory
        this.resolver    = artifactResolver
        this.local       = artifactRepository
        this.remoteRepos = remoteArtifactRepositories

        for ( CopyResource resource in resources().findAll { GMojoUtils.runIf( it.runIf ) })
        {
            boolean verbose         = GMojoUtils.choose( resource.verbose,        verbose        )
            boolean failIfNotFound  = GMojoUtils.choose( resource.failIfNotFound, failIfNotFound )
            boolean resourceHandled = false

            resource.includes = update( resource.includes )
            resource.excludes = update( resource.excludes )

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

        def  isNet             = GCommons.net().isNet( resource.directory )
        File sourceDirectory   = ( resource.mkdir ) ? null                            : // Only <targetPath> is active
                                 ( isNet          ) ? GCommons.file().tempDirectory() : // Temp dir to download the files to
                                                      new File( resource.directory )    // Directory to cleanup or copy

        def( List<String> includes, List<String> excludes ) = [ resource.includes, resource.excludes ].collect {
            ( isNet || ( ! it )) ? null : new ArrayList<String>( it )
            // Net operation or null/empty list - all files are included, none excluded
        }

        if ( ! resource.mkdir )
        {
            if ( isNet )
            {
                assert ( ! sourceDirectory.list()), \
                       "Temporal directory [$sourceDirectory.canonicalPath] for downloading files is not empty?"

                download( resource, resource.directory, sourceDirectory, verbose )

                assert ( sourceDirectory.list() || ( ! failIfNotFound )), \
                       "No files were downloaded from [$resource.directory] " +
                       "and include/exclude patterns ${ resource.includes ?: [] }/${ resource.excludes ?: [] }"
            }
            /**
             * Default excludes are not active for downloaded files: all of them are included, none is excluded
             */
            else if  (( resource.defaultExcludes != 'false' ) && ( defaultExcludes != 'false' ))
            {
                excludes = ( excludes ?: [] ) +
                           ( resource.defaultExcludes ?: defaultExcludes ).split( ',' )*.trim().findAll{ it }
            }
        }

        handleResource( resource, sourceDirectory, includes, excludes, verbose, failIfNotFound )

        if ( isNet ){ GCommons.file().delete( sourceDirectory )}
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
        GCommons.verify().notNullOrEmpty( dependencies )

        if ( resource.dependenciesAtM2 )
        {
            resolveDependencies( dependencies ).each {
                CopyDependency d ->
                File m2File = GCommons.verify().file( d.artifact.file )
                handleResource( resource, m2File.parentFile, [ m2File.name ], null, verbose, true )
            }
        }
        else
        {
            File tempDirectory      = GCommons.file().tempDirectory()
            int  dependenciesCopied = 0

            resolveDependencies( dependencies, tempDirectory, resource.stripVersion ).each {
                CopyDependency d ->
                copyArtifact( d ) // Copy <dependency> to temp directory
                dependenciesCopied++
            }

            // Zero dependencies can be copied if some of them are optional and are not resolved.
            handleResource( resource, tempDirectory, null, null, verbose, ( dependenciesCopied > 0 ))
            GCommons.file().delete( tempDirectory )
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
                                                       File                 outputDirectory = GCommons.constants().USER_DIR_FILE,
                                                       boolean              stripVersion    = false )
    {
        GCommons.verify().notNullOrEmpty( dependencies )
        GCommons.verify().directory( outputDirectory )

        List<CopyDependency> dependenciesResolved = ( List<CopyDependency> ) dependencies.inject( [] ) {
            List<CopyDependency> list, CopyDependency d ->
            list.addAll( d.groupId ? [ d ] : CopyMojoUtils.getFilteredDependencies( d ))
            list
        }.collect {
            CopyDependency d ->

            GCommons.verify().notNullOrEmpty( d.groupId, d.artifactId, d.version )

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
     * Downloads files required using remote location specified.
     *
     * @param resource        current copy resource
     * @param remoteLocation  remote location: http, ftp or scp URL.
     * @param targetDirectory directory to download the files to
     * @param verbose         verbose logging
     */
    private void download ( CopyResource resource, String remoteLocation, File targetDirectory, boolean verbose )
    {
        GCommons.verify().notNull( resource )
        assert GCommons.net().isNet( remoteLocation )
        GCommons.verify().directory( targetDirectory )

        if ( GCommons.net().isHttp( remoteLocation ))
        {
            NetworkUtils.httpDownload( targetDirectory,
                                       remoteLocation,
                                       verbose )
        }
        else if ( GCommons.net().isFtp( remoteLocation ))
        {
            NetworkUtils.ftpDownload( targetDirectory,
                                      remoteLocation,
                                      resource.includes,
                                      resource.excludes,
                                      resource.listFilter,
                                      mavenProject,
                                      mavenSession,
                                      groovyConfig,
                                      verbose,
                                      resource.curl,
                                      resource.wget,
                                      resource.retries,
                                      resource.timeout )
        }
        else if ( GCommons.net().isScp( remoteLocation ))
        {
            NetworkUtils.scpDownload( targetDirectory,
                                      remoteLocation,
                                      resource.includes,
                                      resource.excludes,
                                      verbose,
                                      resource.curl,
                                      resource.wget )
        }
        else
        {
            throw new RuntimeException( "Unrecognized remote location [$remoteLocation]" )
        }
    }


    /**
     * Analyzes patterns specified and updates them if required:
     * - if any of them is comma or space-separated, splits it to additional patterns
     * - if any of them starts with "file:" or "classpath:", each line in the resource
     *   loaded is converted to a pattern
     *
     * @param patterns patterns to analyze
     * @return updated patterns list
     */
    private List<String> update( List<String> patterns )
    {
        if ( ! patterns ) { return patterns }

        List<String> newPatterns = []

        for ( pattern in patterns*.trim().findAll { it })
        {
            newPatterns.addAll(
                pattern.startsWith( 'file:'      ) ? GMojoUtils.readLines( new File( pattern.substring( 'file:'.length()))) :
                pattern.startsWith( 'classpath:' ) ? GMojoUtils.readLines( pattern.substring( 'classpath:'.length()))       :
                pattern.contains( ',' )            ? pattern.split( ',' )*.trim().findAll{ it }                             :
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
        GCommons.verify().notNull( resource )
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
            GCommons.verify().directory( sourceDirectory )
        }

        def filesToProcess = []

        for ( path in resource.targetPaths())
        {
            File targetPath = new File( GCommons.verify().notNullOrEmpty( path ))

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
                if ( includes ) { includes.each{ files.addAll( GCommons.file().files( sourceDirectory, [ it ], excludes, true, false, failIfNotFound )) }}
                else            {                files.addAll( GCommons.file().files( sourceDirectory, null,   excludes, true, false, failIfNotFound ))  }

                for ( file in filter( files, resource.filter, verbose, failIfNotFound ))
                {
                    if ( resource.unpack )
                    {
                        zipEntries ? GCommons.file().unpackZipEntries( file, targetPath, zipEntries, resource.preservePath, verbose ) :
                                     GCommons.file().unpack( file, targetPath )
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
        boolean skipIdentical = (( ! resource.process ) && /* If file is processed - it is not skipped */
                                 GMojoUtils.choose ( resource.skipIdentical, skipIdentical ))
        /**
         * Location where the file will be copied to
         */
        String filePath = new File( targetPath, resource.preservePath ? MojoUtils.relativePath( sourceDirectory, file ) :
                                                                        file.name ).canonicalPath
        assert filePath.endsWith( file.name )

        if ( resource.destFileName )
        {
            filePath = filePath.substring( 0, filePath.lastIndexOf( file.name )) + resource.destFileName
        }

        File    targetFile = new File( filePath )
        boolean copied     = MojoUtils.copy( file,
                                             targetFile,
                                             skipIdentical,
                                             resource.replaces(),
                                             resource.filtering,
                                             resource.encoding,
                                             fileFilter,
                                             mavenProject,
                                             mavenSession,
                                             verbose )

        copied ? GCommons.verify().file( targetFile ) : null
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
        GCommons.file().pack( sourceDirectory, targetPath, includes, excludes, true, failIfNotFound )

        if ( resource.attachArtifact )
        {
            mavenProjectHelper.attachArtifact( mavenProject,
                                               GCommons.file().extension( targetPath ),
                                               resource.artifactClassifier,
                                               targetPath )
        }

        if ( resource.deploy )
        {
            String[] data = resource.deploy.split( /\|/ )

            assert ( data?.size()?.any{( it == 3 ) || ( it == 4 )}), \
                   "Failed to split <deploy> tag data [$resource.deploy]. " +
                   'It should be of the following form: "<deployUrl>|<groupId>|<artifactId>|<version>[|<classifier>]"'

            def ( String url, String groupId, String artifactId, String version ) = data[ 0 .. 3 ].collect { String s -> GCommons.verify().notNullOrEmpty( s ) }
            def classifier = (( data.size() == 4 ) ? GCommons.verify().notNullOrEmpty( data[ 4 ] ) : null )

            MojoUtils.deploy( targetPath, url, groupId, artifactId, version, classifier,
                              mavenProject, mavenSession, pluginManager )
        }

        GCommons.verify().file( targetPath )
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

        GCommons.file().mkdirs( targetPath )
        if ( verbose ){ log.info( "Directory [$targetPath.canonicalPath] created" )}

        GCommons.verify().directory( targetPath )
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
        if ( failIfNotFound ) { GCommons.verify().directory( sourceDirectory ) }

        if ( sourceDirectory.isDirectory())
        {
            def filesDeleted = filter( GCommons.file().files( sourceDirectory, includes, excludes, true, false, failIfNotFound ),
                                       filterExpression, verbose, failIfNotFound )

            GCommons.file().delete( filesDeleted as File[] )

            if ( cleanEmptyDirectories )
            {
                List<File> directoriesDeleted = ( sourceDirectory.splitWith( 'eachDirRecurse' ) + sourceDirectory ).
                                                findAll{ File f -> f.isDirectory() && ( GCommons.file().directorySize( f ) == 0 )}

                GCommons.file().delete( directoriesDeleted as File[] )
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

        GCommons.verify().exists( files as File[] )

        if ( ! filterExpression )
        {
            return files
        }

        String           expression    = GCommons.verify().notNullOrEmpty( FILTERS[ filterExpression ] ?: filterExpression )
        Object           o             = GMojoUtils.groovy( expression, Object, groovyConfig, 'files', files )
        Collection<File> filesIncluded = (( o instanceof File       ) ? [ ( File ) o ]            :
                                          ( o instanceof Collection ) ? (( Collection<File> ) o ) :
                                                                        null )
        assert ( filesIncluded != null ), \
               "Executing Groovy expression [$expression] produced [$o] of type [${ o.class.name }]. " +
               "It should be an instance of File or Collection<File>."

        if ( verbose )
        {
            log.info( "Files left after applying <filter>:${ GCommons.constants().CRLF }${ GMojoUtils.stars( filesIncluded ) }" )
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

        GCommons.verify().exists( files as File[] )

        if ( processExpression )
        {
            GMojoUtils.groovy( processExpression, null, groovyConfig, 'files', files )
        }
    }
}
