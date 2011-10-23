package com.goldin.plugins.copy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.Replace
import org.apache.maven.model.Resource
import org.gcontracts.annotations.*



/**
 * <resource> data container
 */
@SuppressWarnings( [ 'CloneableWithoutClone', 'StatelessClass' ] )
class CopyResource extends Resource implements Cloneable
{
    String   targetRoots
    String[] targetPaths
    String[] targetPathsResolved

    String[] targetPaths()
    {
        if ( targetPathsResolved ) { return targetPathsResolved }

        def paths = general().array( this.targetPaths, targetPath, String )

        if ( targetRoots )
        {
            List<String> targetRootsSplit = split( targetRoots )
            if ( targetRootsSplit )
            {
                paths = [ targetRootsSplit, paths as List ].combinations().collect { it[ 0 ] + '/' + it[ 1 ] }
            }
        }

        assert paths
        targetPathsResolved = new CopyMojoHelper().with { paths.collect { canonicalPath( it ) }} as String[]
        assert targetPathsResolved
        targetPathsResolved
    }


    Replace[] replaces
    Replace   replace
    Replace[] replaces () { general().array( this.replaces, this.replace, Replace ) }


    CopyDependency[] dependencies
    CopyDependency   dependency
    CopyDependency[] dependencies () { general().array( this.dependencies, this.dependency, CopyDependency ) }

    String[] zipEntries
    String   zipEntry
    String[] zipEntries () { general().array( this.zipEntries, this.zipEntry, String ) }


    /**
     * Boolean flags that we need 3 states for: true, false, not initialized
     */
    Boolean verbose
    Boolean failIfNotFound
    Boolean skipIdentical
    Boolean useTrueZipForPack
    Boolean useTrueZipForUnpack
    Boolean filterWithDollarOnly
    Boolean dependenciesAtM2  // "false" - all <dependencies> are copied to temp directory first,
                              //          "stripVersion" is active
                              //          <filter> and <process> operate on all of them at once
                              //          dependencies are not processes in the same order they are declared
                              // "true" - every dependency is served from local Maven repo,
                              //          "stripVersion" is not active
                              //          <filter> and <process> operate on each dependency individually
                              //          dependencies are processes in the same order they are declared

    boolean preservePath          = false
    boolean stripVersion          = false
    boolean clean                 = false
    boolean cleanEmptyDirectories = false
    boolean mkdir                 = false
    boolean unpack                = false
    boolean pack                  = false
    boolean update                = false
    boolean attachArtifact        = false
    boolean move                  = false

    int     retries = 5     // Number of retries for FTP download
    long    timeout = 3600  // FTP download timeout (in seconds)

    String  description
    String  runIf
    String  encoding = 'UTF-8'
    String  wget
    String  curl
    String  destFileName
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

        if ( net().isNet( filePath ))
        {
            directory = filePath
        }
        else
        {   /**
             * Not verifying file or its parent for existence - it may not be available
             * when plugin is configured if created by previous <resource> at run-time
             */
            new File( filePath ).canonicalFile.with {
                assert parent, "File [$delegate] has no parent directory"
                directory = parent
                include   = name
            }
        }
    }


    /**
     * Retrieves {@code <dependenciesAtM2>} value.
     * If it is defined - the corresponding value is returned.
     * If this resource makes no use of {@code <stripVersion>}, {@code <filter>}, and {@code <process>} - true is returned
     * as it is safe not to copy dependencies to the temporal folder.
     * Otherwise, false is returned.
     *
     * @return
     */
    boolean dependenciesAtM2 ()
    {
        // noinspection GroovyConditionalCanBeElvis
        ( this.dependenciesAtM2 != null ) ? this.dependenciesAtM2 : ( ! ( this.stripVersion || this.filter || this.process ))
    }


    @Override
    String toString ()
    {
        "Target path(s) ${ targetPaths() }, directory [$directory], includes $includes, excludes $excludes, dependencies ${ dependencies() }, " +
        "clean [$clean], mkdir [$mkdir], pack [$pack], unpack [$unpack]"
    }
}
