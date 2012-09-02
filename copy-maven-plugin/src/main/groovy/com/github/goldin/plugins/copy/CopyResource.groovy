package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.Replace
import org.apache.maven.model.Resource
import org.gcontracts.annotations.*



/**
 * <resource> data container
 */
@SuppressWarnings( [ 'CloneableWithoutClone', 'StatelessClass' ] )
final class CopyResource extends Resource implements Cloneable
{
    /**
     * Provides a copy of this resource for copying files from {@code targetPathFile} to {@code directoryFile}.
     *
     * @param mojo            plugin instance to read its configurations
     * @param targetPathFile  path to copy the files to
     * @param directoryFile   directory to copy the files from
     * @param includePatterns include patterns
     * @param excludePatterns exclude patterns
     *
     * @return copy of this resource for copying files from {@code targetPath} to {@code directory}
     */
    @Requires({ targetPathFile && directoryFile })
    CopyResource makeCopy ( CopyMojo     mojo,
                            File         targetPathFile,
                            File         directoryFile,
                            List<String> includePatterns,
                            List<String> excludePatterns )
    {
        CopyResource newResource = new CopyResource()

        newResource.with { general().with {
            startTime             = this.startTime
            targetPath            = targetPathFile.canonicalPath
            directory             = directoryFile.canonicalPath
            includes              = includePatterns
            excludes              = excludePatterns
            preservePath          = true
            skipIdentical         = false
            replaces              = this.replaces() as Replace[]
            filtering             = this.filtering
            filter                = this.filter
            encoding              = this.encoding
            destFileName          = this.destFileName
            destFilePrefix        = this.destFilePrefix
            destFileSuffix        = this.destFileSuffix
            destFileExtension     = this.destFileExtension
            defaultExcludes       = choose( this.defaultExcludes,      mojo.defaultExcludes())
            failIfNotFound        = choose( this.failIfNotFound,       mojo.failIfNotFound )
            filterWithDollarOnly  = choose( this.filterWithDollarOnly, mojo.filterWithDollarOnly )
            nonFilteredExtensions = this.nonFilteredExtensions ?:      mojo.nonFilteredExtensions
        }}

        newResource
    }

    String[] targetRoots
    String   targetRoot

    String[] targetPaths
    String[] targetPathsResolved

    String[] targetPaths()
    {
        if ( targetPathsResolved != null ) { return targetPathsResolved }

        List<String> paths = split( general().list( targetPaths, targetPath ).join( ',' ))
        assert     ( paths || clean ), \
                     '<targetPath>/<targetPaths> need to be defined for resources that do not perform <clean> operation'

        if ( paths && ( targetRoots || targetRoot ))
        {
            List<String> targetRootsSplit = split( general().list( targetRoots, targetRoot ).join( ',' ))
            if ( targetRootsSplit )
            {
                paths = [ targetRootsSplit, paths ].combinations().collect { it[ 0 ] + '/' + it[ 1 ] }
            }
        }

        assert ( targetPathsResolved == null )
        targetPathsResolved = paths.collect { String path -> canonicalPath( path ) } as String[]
    }


    Replace[]     replaces
    Replace       replace
    List<Replace> replaces () { general().list( this.replaces, this.replace ) }

    CopyManifest manifest

    CopyDependency[]     dependencies
    CopyDependency       dependency
    List<CopyDependency> dependencies () { general().list( this.dependencies, this.dependency ) }

    String[] zipEntries
    String   zipEntry
    String[] zipEntriesExclude
    String   zipEntryExclude

    List<String> zipEntries ()        { split( general().list( this.zipEntries,        this.zipEntry        ).join( ', ' )) }
    List<String> zipEntriesExclude () { split( general().list( this.zipEntriesExclude, this.zipEntryExclude ).join( ', ' )) }


    /**
     * Boolean flags that we need 3 states for: true, false, undefined
     */
    Boolean stripVersion
    Boolean verbose
    Boolean failIfNotFound
    Boolean failOnError
    Boolean skipIdentical
    Boolean skipPacked
    Boolean skipUnpacked
    Boolean useTrueZipForPack
    Boolean useTrueZipForUnpack
    Boolean filterWithDollarOnly
    Boolean dependenciesAtM2  // "false" - all <dependencies> are copied to temp directory first,
                              //          "stripVersion" is active,
                              //          <filter> and <process> operate on all of them at once,
                              //          dependencies are not processes in the same order they are declared
                              // "true" - every dependency is served from local Maven repo,
                              //          "stripVersion" is not active,
                              //          <filter> and <process> operate on each dependency individually,
                              //          dependencies are processes in the same order they are declared

    boolean preservePath          = false
    boolean clean                 = false
    boolean cleanEmptyDirectories = false
    boolean mkdir                 = false
    boolean unpack                = false
    boolean pack                  = false
    boolean update                = false
    boolean attachArtifact        = false
    boolean move                  = false

    boolean stop                  = false // For troubleshooting only: halt build execution after resource is processed
    String  failsWith             = ''    // For troubleshooting only: resource processing should fail with exception specified

    int     compressionLevel = 9     // Zip compression level
    int     retries          = 5     // Number of retries for FTP download
    long    timeout          = 3600  // FTP download timeout (in seconds)
    long    startTime        = -1    // Time when this resource started to be processed
    long    endTime          = -1    // Time when this resource finished to be processed

    String  description
    String  runIf
    String  encoding = 'UTF-8'
    String  wget
    String  curl
    String  destFileName
    String  destFilePrefix
    String  destFileSuffix
    String  destFileExtension
    String  filter
    String  listFilter
    String  process
    String  deploy
    String  artifactClassifier
    String  defaultExcludes
    String  prefix
    String  nonFilteredExtensions

    /**
     * Single {@code <include>} shortcut
     */
    @Requires({ include })
    @Ensures({ includes == [ include ] })
    void setInclude( String include ) { includes = [ include ] }

    /**
     * Single {@code <exclude>} shortcut
     */
    @Requires({ exclude })
    @Ensures({ excludes == [ exclude ] })
    void setExclude( String exclude ) { excludes = [ exclude ] }


    /**
     * {@code <file>} configuration shortcut instead of specifying {@code <directory>} and {@code <includes>}
     * @param filePath path of the file to be included
     */
    @Requires({ filePath })
    void setFile ( String filePath )
    {
        assert filePath?.trim()?.length()

        /**
         * It is not possible to use GCommons.net().isNet() - it is not available in Maven 2 when setter is called
         */
        if ( [ 'ftp', 'scp', 'http' ].any{ filePath.startsWith( "$it://" ) })
        {
            directory = filePath
        }
        else
        {   /**
             * File may not be available yet
             */
            new File( filePath ).canonicalFile.with {
                assert parent, "File [$delegate] has no parent directory"
                directory = parent
                include   = name
            }
        }
    }


    /**
     * Determines whether dependencies are retrieved from .m2 directory rather than copying them to temp directory first.
     *
     * If it is defined - the corresponding value is returned.
     * If this resource specifies a single dependency - true is returned.
     * If this resource makes no use of {@code <filter>} or {@code <process>} - true is returned.
     *
     * Otherwise, false is returned.
     *
     * @return whether dependencies for the current resource should be served from the local Maven repo
     */
    boolean dependenciesAtM2 ()
    {
        if ( this.dependenciesAtM2 != null ) { return this.dependenciesAtM2 }

        boolean singleDependency = ( dependencies().size() == 1 ) && ( dependencies()[ 0 ].single )
        return (( singleDependency ) || ( ! ( this.filter || this.process )))
    }


    /**
     * Determines if current resource requires a local processing before being uploaded to remote directory.
     *
     * @return true, if current resource requires a local processing before being uploaded,
     *         false otherwise
     */
    boolean needsProcessingBeforeUpload()
    {
        filter       || filtering      || process        || deploy       ||
        replaces()   || manifest       || attachArtifact || stripVersion ||
        destFileName || destFilePrefix || destFileSuffix || destFileExtension
    }


    @Override
    String toString ()
    {
        "Target path${ targetPaths().size() == 1 ? '' : 's' } ${ targetPaths() }, " +
        "directory [${ directory ?: '' }], " +
        "dependencies ${ dependencies() }"
    }
}
