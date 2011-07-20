package com.goldin.plugins.copy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.Replace
import org.apache.maven.model.Resource


/**
 * <resource> data container
 */
class CopyResource extends Resource
{

    String targetRoots

    /**
     * Single/plural configuration shortcuts
     */

    String[] targetPaths
    String[] targetPaths()
    {
        def paths = generalBean().array( this.@targetPaths, targetPath, String )

        if ( targetRoots )
        {
            def  targetRootsSplit = targetRoots.split( ',' )*.trim().findAll{ it }
            if ( targetRootsSplit )
            {
                paths = targetRootsSplit.collect{ String targetRoot -> paths.collect { targetRoot + '/' + it }}.flatten()
            }
        }

        paths
    }

    Replace[] replaces
    Replace   replace
    Replace[] replaces () { generalBean().array( this.replaces, this.replace, Replace ) }


    CopyDependency[] dependencies
    CopyDependency   dependency
    CopyDependency[] dependencies () { generalBean().array( this.dependencies, this.dependency, CopyDependency ) }

    String[] zipEntries
    String   zipEntry
    String[] zipEntries () { generalBean().array( this.zipEntries, this.zipEntry, String ) }


    /**
     * Boolean flags that we need 3 states for: true, false, not initialized
     */
    Boolean verbose
    Boolean failIfNotFound
    Boolean skipIdentical

    boolean preservePath          = false
    boolean stripVersion          = false
    boolean clean                 = false
    boolean cleanEmptyDirectories = false
    boolean mkdir                 = false
    boolean unpack                = false
    boolean pack                  = false
    boolean update                = false
    boolean attachArtifact        = false
    Boolean dependenciesAtM2               // "false" - all <dependencies> are copied to temp directory first,
                                           //          "stripVersion" is active
                                           //          <filter> and <process> operate on all of them at once
                                           //          dependencies are not processes in the same order they are declared
                                           // "true" - every dependency is served from local Maven repo,
                                           //          "stripVersion" is not active
                                           //          <filter> and <process> operate on each dependency individually
                                           //          dependencies are processes in the same order they are declared

    int     retries        = 5          // Number of retries for FTP download
    long    timeout        = 3600       // FTP download timeout (in seconds)

    String  description
    String  runIf
    String  encoding       = 'UTF-8'
    String  wget
    String  curl
    String  destFileName
    String  filter
    String  listFilter
    String  process
    String  deploy
    String  artifactClassifier
    String  defaultExcludes


    /**
     * Single {@code <include>} shortcut
     */
    public void setInclude( String include ) { setIncludes([ include ]) }


    /**
     * Single {@code <exclude>} shortcut
     */
    public void setExclude( String exclude ) { setExcludes([ exclude ]) }


    /**
     * {@code <file>} configuration shortcut instead of specifying {@code <directory>} and {@code <includes>}
     * @param filePath path of the file to be included
     */
    public void setFile ( String filePath )
    {
        assert filePath?.trim()?.length()
        def path = filePath.toLowerCase()

        if ( path.startsWith( 'ftp://' ) || path.startsWith( 'scp://' ) || path.startsWith( 'http://' ))
        {
            setDirectory( filePath )
        }
        else
        {   /**
             * Not verifying file or its parent for existence - it may not be available
             * when plugin is configured if created by previous <resource> at run-time
             */
            File file = new File( filePath )
            assert file.parent, "File [$file] has no parent directory"

            setDirectory( file.parent )
            setInclude( file.name )
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
    boolean getDependenciesAtM2()
    {
        ( this.@dependenciesAtM2 != null ) ? this.@dependenciesAtM2 :
                                             ( ! ( this.@stripVersion || this.@destFileName || this.@filter || this.@process ))
    }


    @Override
    public String toString ()
    {
        "Target path(s) ${ targetPaths() }, directory [$directory], includes $includes, excludes $excludes, dependencies ${ dependencies() }, " +
        "clean [$clean], mkdir [$mkdir], pack [$pack], unpack [$unpack]"
    }
}
