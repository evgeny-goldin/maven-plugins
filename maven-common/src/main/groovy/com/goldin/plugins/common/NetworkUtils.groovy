package com.goldin.plugins.common

import com.goldin.gcommons.GCommons
import com.goldin.gcommons.util.GroovyConfig
import com.goldin.org.apache.tools.ant.taskdefs.optional.net.FTP
import com.goldin.plugins.common.GMojoUtils.EXEC_OPTION
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import static com.goldin.plugins.common.GMojoUtils.*

 /**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Various network related util methods
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
class NetworkUtils
{
    private static Log getLog () { ThreadLocals.get( Log.class ) }


    /**
     * Downloads file from URL to directory specified.
     *
     * @param parentDirectory directory to store the file downloaded
     * @param httpUrl             URL to download the file
     * @return reference to file downloaded, stored in the directory specified
     *
     * @throws RuntimeException if fails to download the file
     */
    public static File httpDownload ( File    parentDirectory,
                                      String  httpUrl,
                                      boolean verbose )
    {
        GCommons.file().mkdirs( parentDirectory )
        assert ( GCommons.net().isHttp( httpUrl ))

        BufferedInputStream  bis           = null
        BufferedOutputStream bos           = null
        String               fileName      = httpUrl.substring( httpUrl.lastIndexOf( '/' ) + 1 )
        File                 localFile     = new File( parentDirectory, fileName )

        log.info( "Downloading [$httpUrl] to [$localFile.canonicalPath]" )

        try
        {
            bis           = new BufferedInputStream ( new URL( httpUrl ).openStream())
            bos           = new BufferedOutputStream( new FileOutputStream( localFile ))
            byte[] buffer = new byte[ 10 * 1024 ];

            for ( int bytesRead;
                    (( bytesRead = bis.read( buffer )) > -1 );
                        bos.write( buffer, 0, bytesRead )){ /* Do nothing */ }

        }
        catch ( Exception e )
        {
            throw new RuntimeException( "Failed to download [$httpUrl] to [$localFile.canonicalPath]: $e", e )
        }
        finally
        {
            GCommons.io().close( bis, bos )
        }

        GCommons.verify().file( localFile )
        if ( verbose ) { log.info( "[$httpUrl] downloaded to [$localFile.canonicalPath]" )}
        localFile
    }


    /**
    * Executes "sshexec" using the command provided on the server specified
    */
    static void sshexec ( String  command,
                          String  username,
                          String  password,
                          String  host,
                          String  directory )
    {
        new AntBuilder().sshexec( command     : "cd ${ directory } ${ command }",
                                  host        : host,
                                  username    : username,
                                  password    : password,
                                  trust       : true,
                                  failonerror : true )
    }


    static void ftpDownload ( File         localDirectory,
                              String       remotePath,
                              List<String> includes,
                              List<String> excludes,
                              String       listFilter,
                              MavenProject project,
                              MavenSession session,
                              GroovyConfig config,
                              boolean      verbose,
                              String       curl,
                              String       wget,
                              int          retries    = 5,
                              long         timeoutSec = 3600 /* 1 hour */ )
    {
        GCommons.file().mkdirs( localDirectory )
        assert includes, "<include> or <includes> should be specified for FTP download"

        Map<String, String> ftpData = GCommons.net().parseNetworkPath( remotePath )
        String  remotePathLog       = "${ ftpData.protocol }://${ ftpData.username }@${ ftpData.host }${ ftpData.directory }"
        boolean isList              = ( curl || wget )
        def     commandParts        = ( curl   ?: wget ?: null )?.split( /\|/ ) // "wget|ftp-list.txt|true|false"
        def     command             = ( isList ? commandParts[ 0 ] : null )
        def     listFile            = ( isList ? new File (( commandParts.size() > 1 ) ?       commandParts[ 1 ]   : 'ftp-list.txt' ) : null )
        def     deleteListFile      = ( isList && commandParts.size() > 2 ) ? Boolean.valueOf( commandParts[ 2 ] ) : true
        def     nativeListing       = ( isList && commandParts.size() > 3 ) ? Boolean.valueOf( commandParts[ 3 ] ) : false
        def     listFilePath        = listFile?.canonicalPath
        def     localDirectoryPath  = localDirectory.canonicalPath
        long    t                   = System.currentTimeMillis()
        def     retryCount          = 1
        def     newList             = true
        def     previousList        = ''
        def     includesConverted   = null
        def     excludesConverted   = null

        if ( listFile ) { GCommons.file().mkdirs( listFile.parentFile ) }

        if ( ftpData.directory != '/' )
        {   /**
             * If any of include or exclude patterns contain remote directory, it needs to be removed: pl-265
             */
            String remoteDirectory = ftpData.directory.replaceAll( /^\/*/, '' )                         // Without leading slashes
            def c = { it.replace( remoteDirectory, '' ).replace( '\\', '/' ).replaceAll( /^\/*/, '' )}  // Remove remote directory
            if ( includes.any{ it.contains( remoteDirectory ) }) { includesConverted = includes.collect( c ) }
            if ( excludes.any{ it.contains( remoteDirectory ) }) { excludesConverted = excludes.collect( c ) }
        }

        while  ( true )
        {
            try
            {
                log.info( """
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Attempt [$retryCount]: downloading files from [$remotePathLog] to [$localDirectoryPath]
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Include patterns  :
${ stars( includes ) }
Converted to      : ${ ( includesConverted != null ) ? GCommons.constants().CRLF + stars( includesConverted ) : 'Not converted' }
Exclude patterns  :
${ stars( excludes ) }
Converted to      : ${ ( excludesConverted != null ) ? GCommons.constants().CRLF + stars( excludesConverted ) : 'Not converted' }
External command  : [${ command      ?: 'None' }]
List file         : [${ listFilePath ?: 'None' }]
Delete list file  : [$deleteListFile]
Native FTP listing: [$nativeListing]
Verbose           : [$verbose]
Number of retries : [$retries]
Timeout           : [$timeoutSec] sec (${ timeoutSec.intdiv( GCommons.constants().SECONDS_IN_MINUTE ) } min)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~""" )

                if ( includesConverted != null ) { includes = includesConverted }
                if ( excludesConverted != null ) { excludes = excludesConverted }
                if ( isList && newList ) { listFile.write( '' ) } // Will be appended with data

                previousList = ( listFile?.isFile() ? listFile.text : '' )

                if ( isList && nativeListing )
                {
                    // Calculating remote directory where files matched by glob patterns are located
                    def c               = { String s -> s.replace( '\\', '/' ).replaceFirst( /^\//, '' ).replaceAll( /\/?[^\/]+$/, '' ) }
                    def fileDir         = c( includes[ 0 ] )
                    def remoteDirectory = "${ ftpData.directory }/$fileDir".replaceAll( /\/+/, '/' )

                    // Making sure all glob patterns specify files in the same remote directory
                    assert includes.every{ c( it ) == fileDir }, \
                           "Glob patterns [$includes] specify files from different remote directories - not supported yet, vote for 'pl-264'"
                    
                    for ( file in GCommons.net().listFiles( remotePath, includes, excludes, 1 ))
                    {
                        listFile.append( FTP.listSingleFile( ftpData.host, remoteDirectory, file.name.replaceAll( /.*\//, '' ), file.size ))
                        listFile.append( '\n' )
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
                                                retriesAllowed  : retries,
                                                passive         : true,
                                                binary          : true )
                    {
                        fileset( dir       : GMojoUtils.path( localDirectory ),
                                 includes  : includes.join( ',' ),
                                 excludes  : excludes.join( ',' ))
                    }
                }

                if ( isList )
                {
                    def listFileText  = GCommons.verify().file( listFile ).text
                    log.info( "List file is stored at [$listFilePath]:${ GCommons.constants().CRLF }${ listFileText }" )

                    /**
                     * Creating a Map of files: "file name" => "file size"
                     * Each line in a list file looks like
                     * "ftp://orywevgeny.clearforest.com//od/small/OfficersDirectors03_GL_f_20101120_1of1.xml.zip|23505456"
                     * {@link com.goldin.org.apache.tools.ant.taskdefs.optional.net.FTP#listFile}
                     */
                    Map listFileMap = listFileText.splitWith( 'eachLine' ).inject( [:], {
                        Map map, String line ->

                        def    ( ftpUrl,   fileSize ) = line.split( /\|/ )
                        assert ( ftpUrl && fileSize )

                        assert ( ! map.containsKey( ftpUrl )) : "[$ftpUrl] appears more than once in [$listFilePath]"
                        assert (( fileSize as Long ) > -1   ) : "[$ftpUrl] has a negative size [$fileSize] in [$listFilePath]"

                        map[ ftpUrl ] = fileSize as Long
                        map
                    })

                    if ( listFilter )
                    {
                        def o        = GMojoUtils.groovy( listFilter, Object, config, 'files', listFileMap )
                        def filesSet = new HashSet(( o instanceof Collection ) ? o : [ o ] ) // Set<String> of file names to include
                        listFileMap  = listFileMap.findAll{ filesSet.contains( it.key )}   // Filtering all Map entries with a Set

                        if ( verbose )
                        {
                            log.info( "Files to download after applying <listFilter>:${ GCommons.constants().CRLF }${ stars( listFileMap.keySet()) }" )
                        }
                    }

                    def nFiles = listFileMap.size()
                    log.info( "Downloading [$nFiles] file${ ( nFiles == 1 ) ? '' : 's' } to [$localDirectoryPath]" )

                    listFileMap.eachWithIndex
                    {
                        String ftpUrl, long size, int index ->
                        download( ftpUrl, size, ftpData, localDirectory, curl, command, verbose, timeoutSec, retries, index, nFiles )
                    }

                    if ( deleteListFile ) { GCommons.file().delete( listFile ) }
                }

                long totalTimeMs  = ( System.currentTimeMillis() - t )
                log.info( "Attempt [$retryCount]: done, " +
                               "[${ totalTimeMs.intdiv( GCommons.constants().MILLIS_IN_SECOND ) }] sec "+
                               "(${ totalTimeMs.intdiv( GCommons.constants().MILLIS_IN_MINUTE ) } min)" )
                return
            }
            catch ( e )
            {
                log.info( "Attempt [$retryCount]: failed: $e", e )
                assert (( retryCount++ ) < retries ), \
                       "Failed to download files from [$remotePathLog] to [$localDirectoryPath] after [${ retryCount - 1 }] attempt${ ( retryCount == 2 ) ? '' : 's' }"

                log.info( "Trying again .." )

                if ( listFile?.isFile() && ( ! nativeListing ))
                {   /**
                     * Files that are already listed are added to excludes pattern.
                     * Next listing attempt will append to listing file instead of overwriting it
                     */
                    newList         = false
                    def sessionList = listFile.splitWith( 'eachLine' ) - previousList.splitWith( 'eachLine' )
                    excludes       += sessionList.collect{ // "ftp://server//path/to/file|size" => "path/to/file"
                                                           it.replaceAll( /^ftp:\/\/[^\/]+\/+|\|\d+$/, '' ) }

                    log.info( "Files listed in this session are added to excludes: ${ GCommons.constants().CRLF }${ stars( sessionList ) }" )
                }
            }
        }
    }


    /**
     * Downloads the file specified
     */
    private static void download( String  ftpUrl,
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
            "Not implemented yet" :
            "$command -S -${ verbose ? '' : 'n' }v -O \"$destFile\" -T 300 \"$ftpUrl\" --ftp-user=${ ftpData.username } --ftp-password=${ ftpData.password }"

        GCommons.file().delete( destFile )
        log.info( "[$ftpUrl] => [$destFilePath]: Started (file [${ fileIndex + 1 }] of [$totalFiles], " +
                       "[${ fileSize.intdiv( 1024 ) }] Kb)" )

        if ( verbose )
        {
            log.debug( "[${ exec.replace( ftpData.password, '*****' )}]" )
        }

        def sout = ( verbose ? System.out : devNullOutputStream())
        def serr = ( verbose ? System.err : devNullOutputStream())

        for ( def attempts = 1; ( attempts <= maxAttempts ); attempts++ )
        {
            GCommons.file().delete( destFile )
            execute( exec, EXEC_OPTION.CommonsExec, sout, serr, ( timeoutSec * GCommons.constants().MILLIS_IN_SECOND ), localDirectory )
            long fileSizeNow = GCommons.verify().file( destFile ).length()

            if ( fileSizeNow == fileSize )
            {
                log.info( "[$ftpUrl] => [$destFilePath]: Finished (file [${ fileIndex + 1 }] of [$totalFiles], " +
                               "[${ fileSizeNow.intdiv( 1024 ) }] Kb)" )
                return
            }
            else
            {
                log.info( "[$ftpUrl] => [$destFilePath]: file size [$fileSizeNow] doesn't match original [$fileSize], trying again .." )
            }
        }

        throw new RuntimeException( "Failed to download non-empty file after [$maxAttempts] attempts" )
    }


    static void ftpUpload ( File   file,
                            String username,
                            String password,
                            String host,
                            String directory )
    {
        throw new RuntimeException( "Not implemented yet" )
    }


    static void scpDownload ( File         localDirectory,
                              String       remotePath,
                              List<String> includes,
                              List<String> excludes,
                              boolean      verbose,
                              String       curl,
                              String       wget )
    {
        throw new RuntimeException( "Not implemented yet" )
    }


   /**
    * Uploads file provided to scp location specified
    */
    static void scpUpload ( File   file,
                            String username,
                            String password,
                            String host,
                            String directory )
    {
        /**
         * http://groovy-almanac.org/scp-with-groovy-and-antbuilder/
         * http://ant.apache.org/manual/OptionalTasks/scp.html
         */

        new AntBuilder().scp( file     : file.path,
                              todir    : "${ username }@${ host }:${ directory }",
                              verbose  : true,
                              trust    : true,
                              password : password )
    }


    public static void download ()
    {
        // ??????????????????????????????????????????????????????
    }


    public static void upload ( File file, String remotePath, boolean verbose )
    {
        GCommons.verify().notNull( file )
        GCommons.verify().notNullOrEmpty( remotePath )
        assert GCommons.net().isNet( remotePath )

        Map<String, String> data      = GCommons.net().parseNetworkPath( remotePath )
        String              protocol  = data.get( "protocol" )
        String              username  = data.get( "username" )
        String              password  = data.get( "password" )
        String              host      = data.get( "host" )
        String              directory = data.get( "directory" )

        if ( verbose )
        {
            log.info( "Uploading [${ path( file ) }] to [$protocol://$username@$host$directory]" )
        }

        if ( GCommons.net().isHttp( remotePath ))
        {
            throw new RuntimeException( "Not implemented yet" )
        }
        else if ( GCommons.net().isScp( remotePath ))
        {
            scpUpload( file, username, password, host, directory )
        }
        else if ( GCommons.net().isFtp( remotePath ))
        {
            ftpUpload( file, username, password, host, directory )
        }
        else
        {
            throw new RuntimeException( "Unsupported remote path [$remotePath]" )
        }

        if ( verbose )
        {
            log.info( "[${ path( file ) }] uploaded to [$protocol://$username@$host$directory]" )
        }
    }
}
