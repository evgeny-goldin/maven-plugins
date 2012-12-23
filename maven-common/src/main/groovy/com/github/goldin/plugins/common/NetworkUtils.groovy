package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*

import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Requires


/**
 * General network-related utilities.
 */
class NetworkUtils
{
    private NetworkUtils () {}


    @Requires({ remoteHost && commands })
    static void sshexec( String remoteHost, List<String> commands, boolean verbose )
    {
        final data    = netBean().parseNetworkPath( remoteHost )
        final command = [ "cd $data.directory", *commands ].join( '; ' )
        assert 'scp' == data.protocol

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/sshexec.html
         */

        final Map<String, String> arguments = [
            command     : command,
            host        : data.host,
            username    : data.username,
            verbose     : verbose,
            trust       : true,
            failonerror : true ] +
        sshAuthArguments( data.password ) +
        ( data.port ? [ port : data.port ] : [:] )

        final t = System.currentTimeMillis()
        log.info( "==> Running sshexec [$command] in [${ data.host }:${ data.directory }]" )
        new AntBuilder().sshexec( arguments )
        log.info( "==> Sshexec [$command] run in [${ data.host }:${ data.directory }] (${ System.currentTimeMillis() - t } ms)" )
    }


    /**
    * Downloads file from URL to directory specified.
    *
    * @param parentDirectory directory to store the file downloaded
    * @param httpUrl             URL to download the file
    * @return reference to file downloaded, stored in the directory specified
    *
    * @throws RuntimeException if fails to download the file
    */
    static File httpDownload ( File    parentDirectory,
                               String  httpUrl,
                               boolean verbose )
    {
        fileBean().mkdirs( parentDirectory )
        assert netBean().isHttp( httpUrl )

        String fileName  = httpUrl.substring( httpUrl.lastIndexOf( '/' ) + 1 )
        File   localFile = new File( parentDirectory, fileName )

        log.info( "Downloading [$httpUrl] to [$localFile.canonicalPath]" )

        localFile.withOutputStream { OutputStream os ->  httpUrl.toURL().eachByte( 10240 ) { byte[] buffer, int bytes -> os.write( buffer, 0, bytes ) }}

        verifyBean().file( localFile )
        if ( verbose ) { log.info( "[$httpUrl] downloaded to [$localFile.canonicalPath]" )}
        localFile
    }


    static void scpDownload ( File    localDirectory,
                              String  remotePath,
                              boolean verbose )
    {
        scp ( localDirectory, remotePath, verbose, true )
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

            for ( file in files )
            {
                if ( netBean().isScp( remotePath ))
                {
                    scpUpload( file, remotePath, verbose )
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


    private static void scpUpload ( File    file,
                                    String  remotePath,
                                    boolean verbose )
    {
        scp ( file, remotePath, verbose, false )
    }


    private static void ftpUpload ( File    file,
                                    String  remotePath,
                                    boolean verbose )
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
    private static void scp ( File    file,
                              String  remotePath,
                              boolean verbose,
                              boolean isDownload )
    {
        final data = netBean().parseNetworkPath( remotePath )
        assert 'scp' == data.protocol

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/scp.html
         */

        final localDestination  = file.canonicalPath
        final remoteDestination = "${ data.username }@${ data.host }:${ data.directory }"
        final Map<String, String> arguments = [
            file                 : isDownload ? remoteDestination : localDestination,
            todir                : isDownload ? localDestination  : remoteDestination,
            verbose              : verbose,
            sftp                 : true,
            preserveLastModified : true,
            trust                : true ] +
            sshAuthArguments( data.password ) +
            ( data.port ? [ port : data.port ] : [:] )

        new AntBuilder().scp( arguments )
    }
}
