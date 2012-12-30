package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.gcommons.beans.ExecOption
import com.github.goldin.gcommons.util.GroovyConfig
import com.github.goldin.org.apache.tools.ant.taskdefs.optional.net.FTP
import com.github.goldin.plugins.common.CustomAntBuilder
import com.github.goldin.plugins.common.NetworkUtils
import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Requires


/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Various network related util methods for the plugin.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
@SuppressWarnings([ 'AbcMetric' ])
class DownloadHelper
{
    private DownloadHelper () {}


    /**
     * Downloads files required using remote location specified.
     *
     * @param resource        current copy resource
     * @param remotePath      remote location: http, ftp or scp URL.
     * @param targetDirectory directory to download the files to
     * @param verbose         verbose logging
     * @param groovyConfig    current Groovy configuration
     */
    @SuppressWarnings([ 'GroovyIfStatementWithTooManyBranches' ])
    @Requires({ resource && remotePath && targetDirectory.directory })
    static download ( CopyResource resource, String remotePath, File targetDirectory, boolean verbose, GroovyConfig groovyConfig )
    {
        assert netBean().isNet( remotePath )

        if ( netBean().isHttp( remotePath ))
        {
            NetworkUtils.httpDownload( targetDirectory, remotePath, verbose )
        }
        else if ( netBean().isScp( remotePath ))
        {
            NetworkUtils.scpDownload( targetDirectory, remotePath, verbose )
        }
        else if ( netBean().isFtp( remotePath ))
        {
            ftpDownload( targetDirectory, remotePath, resource, groovyConfig, verbose )
        }
        else
        {
            throw new MojoExecutionException( "Unrecognized download remote path [$remotePath]" )
        }
    }


    @SuppressWarnings([ 'AbcComplexity', 'CyclomaticComplexity', 'MethodSize' ])
    static void ftpDownload ( File         localDirectory,
                              String       remotePath,
                              CopyResource resource,
                              GroovyConfig groovyConfig,
                              boolean      verbose )
    {
        fileBean().mkdirs( localDirectory )
        assert resource.includes, '<include> or <includes> should be specified for FTP download'

        List<String>        includes = new ArrayList<String>( resource.includes )
        List<String>        excludes = new ArrayList<String>( resource.excludes )
        Map<String, String> ftpData  = netBean().parseNetworkPath( remotePath )
        String  remotePathLog        = "${ ftpData.protocol }://${ ftpData.username }@${ ftpData.host }${ ftpData.directory }"
        boolean isList               = ( resource.curl || resource.wget )
        def     commandParts         = split(( resource.curl ?: resource.wget ), '|' ) // "wget|ftp-list.txt|true|false"
        def     command              = ( isList ? commandParts[ 0 ] : null )
        def     listFile             = ( isList ? (( commandParts.size() > 1 ) ? new File ( commandParts[ 1 ] ) :
                                                                                 new File ( constantsBean().USER_DIR_FILE, 'ftp-list.txt' )) :
                                                  null )
        def     deleteListFile       = ( isList && commandParts.size() > 2 ) ? Boolean.valueOf( commandParts[ 2 ] ) : true
        def     nativeListing        = ( isList && commandParts.size() > 3 ) ? Boolean.valueOf( commandParts[ 3 ] ) : false
        def     listFilePath         = listFile?.canonicalPath
        def     localDirectoryPath   = localDirectory.canonicalPath
        long    t                    = System.currentTimeMillis()
        def     retryCount           = 1
        def     newList              = true
        def     previousList         = ''

        if ( listFile ) { fileBean().mkdirs( listFile.parentFile ) }

        if ( ftpData.directory != '/' )
        {   /**
             * If any of include or exclude patterns contain remote directory, it needs to be removed:
             * http://evgeny-goldin.org/youtrack/issue/pl-265
             */
            String remoteDirectory = ftpData.directory.replaceAll( /^\/*/, '' )                    // Without leading slashes
            def c = { it.replace( remoteDirectory, '' ).replace( '\\', '/' ).replaceAll( /^\/*/, '' )}  // Remove remote directory
            if ( includes.any{ it.contains( remoteDirectory ) }) { includes = includes.collect( c ) }
            if ( excludes.any{ it.contains( remoteDirectory ) }) { excludes = excludes.collect( c ) }
        }

        while ( true )
        {
            log.info( """
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Attempt [$retryCount]: downloading files from [$remotePathLog] to [$localDirectoryPath]
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Include patterns  :
${ stars( includes ) }
Exclude patterns  :
${ stars( excludes ) }
External command  : [${ command?.trim()             ?: 'None' }]
List file         : [${ listFilePath?.trim()        ?: 'None' }]
List filter       : [${ resource.listFilter?.trim() ?: 'None' }]
Delete list file  : [$deleteListFile]
Native FTP listing: [$nativeListing]
Verbose           : [$verbose]
Number of retries : [$resource.retries]
Timeout           : [$resource.timeout] sec (${ resource.timeout.intdiv( constantsBean().SECONDS_IN_MINUTE ) } min)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~""" )

            try
            {
                if ( isList && newList ) { listFile.write( '' ) } // Will be appended with data

                previousList = ( listFile?.file ? listFile.text : '' )

                if ( isList && nativeListing )
                {
                    for ( file in netBean().listFiles( remotePath, includes, excludes, 1 ))
                    {
                        listFile.append( FTP.listSingleFile( ftpData.host, file.path, file.size ) + '\n' )
                    }
                }
                else
                {
                    new CustomAntBuilder().ftp( action          : isList ? 'list' : 'get',
                                                listing         : listFile,
                                                listingAppend   : true,  // Custom property: to append a data to a listing file instead of overwriting it
                                                listingFullPath : true,  // Custom property: to list full FTP path of each file instead of default "raw listing"
                                                server          : ftpData.host,
                                                userid          : ftpData.username,
                                                password        : ftpData.password,
                                                remotedir       : ftpData.directory,
                                                verbose         : verbose,
                                                retriesAllowed  : resource.retries,
                                                passive         : true,
                                                binary          : true )
                    {
                        fileset( dir       : localDirectory.canonicalPath,
                                 includes  : includes.join( ',' ),
                                 excludes  : excludes.join( ',' ))
                    }
                }

                if ( isList )
                {
                    def listFileText  = listFile.text
                    log.info( "List file is stored at [$listFilePath]:${ constantsBean().CRLF }${ listFileText }" )

                    /**
                     * Creating a Map of files: "file name" => "file size"
                     * Each line in a list file looks like
                     * "ftp://host.com//od/small/OfficersDirectors03_GL_f_20101120_1of1.xml.zip|23505456"
                     * {@link FTP#listFile}
                     */
                    Map listFileMap = listFileText.readLines().inject( [:] ) {
                        Map map, String line ->

                        def    ( String ftpUrl, String fileSize ) = split( line, '|' )
                        assert ( ftpUrl && fileSize )

                        assert ( ! map.containsKey( ftpUrl )) : "[$ftpUrl] appears more than once in [$listFilePath]"
                        assert (( fileSize as Long ) > -1   ) : "[$ftpUrl] has a negative size [$fileSize] in [$listFilePath]"

                        map[ ftpUrl ] = fileSize as Long
                        map
                    }

                    if ( resource.listFilter )
                    {
                        def o        = eval( resource.listFilter, Object, groovyConfig, 'files', listFileMap )
                        def filesSet = new HashSet( o as List<String> )                   // Set<String> of file names to include
                        listFileMap  = listFileMap.findAll{ filesSet.contains( it.key )}  // Filtering all Map entries with a Set

                        if ( verbose )
                        {
                            log.info( "Files to download after applying <listFilter>:${ constantsBean().CRLF }${ stars( listFileMap.keySet()) }" )
                        }
                    }

                    def nFiles = listFileMap.size()
                    log.info( "Downloading [$nFiles] file${ ( nFiles == 1 ) ? '' : 's' } to [$localDirectoryPath]" )

                    listFileMap.eachWithIndex {
                        String ftpUrl, long size, int index ->
                        downloadFile( ftpUrl, size, ftpData, localDirectory, resource.curl,
                                      command, verbose, resource.timeout, resource.retries, index, nFiles )
                    }

                    if ( deleteListFile ) { fileBean().delete( listFile ) }
                }

                long totalTimeMs = ( System.currentTimeMillis() - t )
                log.info( "Attempt [$retryCount]: done, " +
                          "[${ totalTimeMs.intdiv( constantsBean().MILLIS_IN_SECOND ) }] sec " +
                          "(${ totalTimeMs.intdiv( constantsBean().MILLIS_IN_MINUTE ) } min)" )
                return
            }
            catch ( e )
            {
                log.info( "Attempt [$retryCount]: failed: $e", e )
                assert (( retryCount++ ) < resource.retries ), \
                       "Failed to download files from [$remotePathLog] to [$localDirectoryPath] after " +
                       "[${ retryCount - 1 }] attempt${ ( retryCount == 2 ) ? '' : 's' }"

                log.info( 'Trying again ..' )

                if ( listFile?.file && ( ! nativeListing ))
                {   /**
                     * Files that are already listed are added to excludes pattern.
                     * Next listing attempt will append to listing file instead of overwriting it
                     */
                    newList                  = false
                    List<String> sessionList = listFile.readLines() - previousList.readLines()
                    excludes += sessionList*.replaceAll( /^ftp:\/\/[^\/]+\/+|\|\d+$/, '' ) // "ftp://server//path/to/file|size" => "path/to/file"

                    log.info( "Files listed in this session are added to excludes: ${ constantsBean().CRLF }${ stars( sessionList ) }" )
                }
            }
        }
    }


    /**
     * Downloads the file specified
     */
    private static void downloadFile ( String  ftpUrl,
                                       long    fileSize,
                                       Map     ftpData,
                                       File    localDirectory,
                                       String  curl,
                                       String  command,
                                       boolean verbose,
                                       long    timeoutSec,
                                       int     maxAttempts,
                                       int     fileIndex,
                                       int     totalFiles )
    {
        def fileName     = ftpUrl.substring( ftpUrl.lastIndexOf('/') + 1 )
        def destFile     = new File( localDirectory, fileName )
        def destFilePath = destFile.canonicalPath
        def exec         = curl ?
            'Not implemented yet' :
            "$command -S -${ verbose ? '' : 'n' }v -O \"$destFile\" -T 300 \"$ftpUrl\" --ftp-user=${ ftpData.username } --ftp-password=${ ftpData.password }"

        fileBean().delete( destFile )
        log.info( "[$ftpUrl] => [$destFilePath]: Started (file [${ fileIndex + 1 }] of [$totalFiles], " +
                  "[${ fileSize.intdiv( 1024 ) }] Kb)" )

        if ( verbose )
        {
            log.debug( "[${ exec.replace( ftpData.password, '*****' )}]" )
        }

        def sout = ( verbose ? System.out : nullOutputStream())
        def serr = ( verbose ? System.err : nullOutputStream())

        for ( def attempts = 1; ( attempts <= maxAttempts ); attempts++ )
        {
            fileBean().delete( destFile )
            long t           = System.currentTimeMillis()
            generalBean().execute( exec, ExecOption.CommonsExec, sout, serr, ( timeoutSec * constantsBean().MILLIS_IN_SECOND ), localDirectory )
            long fileSizeNow = verifyBean().file( destFile ).length()

            if ( fileSizeNow == fileSize )
            {
                log.info( "[$ftpUrl] => [$destFilePath]: Finished ([${ ( System.currentTimeMillis() - t ).intdiv( 1000 ) }] sec, " +
                          "file [${ fileIndex + 1 }] of [$totalFiles], " +
                          "[${ fileSizeNow.intdiv( 1024 ) }] Kb)" )
                return
            }

            log.info( "[$ftpUrl] => [$destFilePath]: file size [$fileSizeNow] doesn't match original [$fileSize], trying again .." )
        }

        throw new MojoExecutionException( "Failed to download non-empty file after [$maxAttempts] attempts" )
    }
}
