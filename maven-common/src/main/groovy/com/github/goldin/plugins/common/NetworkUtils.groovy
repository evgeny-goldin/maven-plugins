package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * General network-related utilities.
 */
class NetworkUtils
{
    private NetworkUtils () {}


    @Requires({ remoteHost && command })
    @Ensures ({ result.file })
    static File sshexec( String remoteHost, String command, boolean verbose )
    {
        final data    = netBean().parseNetworkPath( remoteHost )
        assert 'scp' == data.protocol

        final outputFile = fileBean().tempFile()

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/sshexec.html
         */

        final Map<String, String> arguments = [
            command     : command,
            host        : data.host,
            username    : data.username,
            verbose     : verbose,
            trust       : true,
            output      : outputFile.canonicalPath,
            failonerror : true ] +
        sshAuthArguments( data.password ) +
        ( data.port ? [ port : data.port ] : [:] )

        log.info( "Running sshexec [$command] in [${ data.host }:${ data.directory }]" )
        new AntBuilder().sshexec( arguments )

        if ( ! outputFile.file ) { outputFile.write( '' ) }
        outputFile
    }


    /**
    * Downloads file from URL to directory specified.
    *
    * @param targetDirectory directory to store the file downloaded
    * @param url             URL to download the file
    * @return reference to file downloaded, stored in the directory specified
    *
    * @throws RuntimeException if fails to download the file
    */
    @Requires({ targetDirectory.directory && url })
    static File httpDownload ( File targetDirectory, String url, boolean verbose )
    {
        assert netBean().isHttp( url )

        String fileName  = url.substring( url.lastIndexOf( '/' ) + 1 )
        File   localFile = new File( targetDirectory, fileName )

        log.info( "Downloading [$url] to [$localFile.canonicalPath]" )

        localFile.withOutputStream { OutputStream os ->  url.toURL().eachByte( 10240 ) { byte[] buffer, int bytes -> os.write( buffer, 0, bytes ) }}

        verifyBean().file( localFile )
        if ( verbose ) { log.info( "[$url] downloaded to [$localFile.canonicalPath]" )}
        localFile
    }


    @Requires({ targetDirectory.directory && remotePath })
    static void scpDownload ( File targetDirectory, String remotePath, boolean verbose )
    {
        scp ( targetDirectory, remotePath, verbose, true )
    }


    /**
     * Uploads files to remote paths specified.
     *
     * @param remotePaths    remote paths to upload files to
     * @param directory      files directory
     * @param includes       include patterns
     * @param excludes       exclude patterns
     * @param preservePath   whether local path should be preserved when files are uploaded
     * @param verbose        verbose logging
     * @param failIfNotFound whether execution should fail if not files were found
     */
    @Requires({ remotePaths && directory })
    static void upload ( String[]     remotePaths,
                         File         directory,
                         List<String> includes,
                         List<String> excludes,
                         boolean      preservePath,
                         boolean      verbose,
                         boolean      failIfNotFound )
    {
        assert remotePaths
        verifyBean().notNullOrEmpty( remotePaths )
        verifyBean().directory( directory )

        final files = fileBean().files( directory, includes, excludes, true, false, failIfNotFound )

        for ( remotePath in remotePaths )
        {
            assert netBean().isNet( remotePath )

            if ( netBean().isHttp( remotePath ))
            {
                throw new MojoExecutionException( 'HTTP upload is not implemented yet, please vote for http://evgeny-goldin.org/youtrack/issue/pl-312' )
            }

            if ( netBean().isScp( remotePath ))
            {
                createRemoteDirectories( remotePath,
                                         preservePath ? files.collect { it.parentFile.canonicalPath - directory.canonicalPath }.grep() : [],
                                         verbose )
            }

            for ( file in files )
            {
                if ( netBean().isScp( remotePath ))
                {
                    scpUpload( file, remotePath + ( preservePath ? file.parentFile.canonicalPath - directory.canonicalPath : '' ), verbose )
                }
                else if ( netBean().isFtp( remotePath ))
                {
                    ftpUpload( file, remotePath, verbose )
                }
                else
                {
                    throw new MojoExecutionException( "Unsupported remote path [$remotePath]" )
                }
            }
        }
    }


    @Requires({ remotePath && ( directories != null ) })
    static void createRemoteDirectories( String remotePath, List<String> directories, boolean verbose )
    {
        final  data = netBean().parseNetworkPath( remotePath )
        assert data.protocol == 'scp', "<mkdir> only works for remote 'scp' resources, [$remotePath] is not supported"

        final dirsToCreate =
            directories ? directories.collect { data.directory + ( data.directory.endsWith( '/' ) || it.startsWith( '/' ) ? it : "/$it" ) } :
                          [ data.directory ]

        sshexec( "scp://${ data.username }:${ data.password }@${ data.host }:~",
                 "mkdir -p ${ dirsToCreate.collect{ it.contains( ' ' ) ? "'$it'" : it }.join( ' ' )}",
                 verbose )
    }


    @Requires({ file.file && remotePath })
    private static void scpUpload ( File file, String remotePath, boolean verbose )
    {
        scp ( file, remotePath, verbose, false )
    }


    @Requires({ file.file && remotePath })
    private static void ftpUpload ( File file, String remotePath, boolean verbose )
    {
        def data = netBean().parseNetworkPath( remotePath )
        assert 'ftp' == data.protocol
        verifyBean().notNullOrEmpty( data.username, data.password, data.host, data.directory )

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/ftp.html
         */
        new AntBuilder().ftp( action    : 'put',
                              server    : data.host,
                              userid    : data.username,
                              password  : data.password,
                              remotedir : data.directory,
                              verbose   : verbose,
                              passive   : true,
                              binary    : true )
        {
            fileset( file : file.canonicalPath )
        }
    }


    @SuppressWarnings([ 'GroovyStaticMethodNamingConvention' ])
    @Requires({ file.exists() && remotePath })
    private static void scp ( File    file,  // Directory to download to or file to upload
                              String  remotePath,
                              boolean verbose,
                              boolean isDownload )
    {
        final data = netBean().parseNetworkPath( remotePath )
        assert 'scp' == data.protocol

        final localDestination  = file.canonicalPath
        final remoteDestination = "${ data.username }@${ data.host }:${ data.directory }"

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/scp.html
         */

        final Map<String, String> arguments = [
            file                 : isDownload ? remoteDestination : localDestination,
            todir                : isDownload ? localDestination  : remoteDestination,
            verbose              : verbose,
            sftp                 : true,
            preserveLastModified : true,
            trust                : true ] +
            sshAuthArguments( data.password ) +
            ( data.port ? [ port : data.port ] : [:] )

        if ( isDownload ){ log.info( "Downloading [scp://${ data.host }:${ data.directory }] to [$localDestination]" )}
        else             { log.info( "Uploading [$localDestination] to [scp://${ data.host }:${ data.directory }]"   )}

        new AntBuilder().scp( arguments )
    }
}
