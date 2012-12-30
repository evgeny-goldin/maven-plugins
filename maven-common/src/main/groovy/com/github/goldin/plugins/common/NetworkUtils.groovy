package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.execution.MavenSession
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
    static File sshexec( String remoteHost, String command, boolean failOnError = true, boolean verbose = true )
    {
        final pathData = netBean().parseNetworkPath( remoteHost )
        assert 'scp'  == pathData.protocol

        final outputFile = fileBean().tempFile()

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/sshexec.html
         */

        final Map<String, ?> arguments = grepMap(( Map<String,?> ) [
            command     : command,
            host        : pathData.host,
            port        : pathData.port,
            username    : pathData.username,
            verbose     : verbose,
            trust       : true,
            output      : outputFile.canonicalPath,
            failonerror : failOnError ] + sshAuthArguments( pathData.password ))

        log.info( "Running sshexec [$command] in [${ pathData.host }:${ pathData.directory }]" )
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
                throw new MojoExecutionException(
                    'HTTP upload is not implemented yet, please vote for http://evgeny-goldin.org/youtrack/issue/pl-312' )
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
        final  pathData = netBean().parseNetworkPath( remotePath )
        assert pathData.protocol == 'scp', "<mkdir> only works for remote 'scp' resources, [$remotePath] is not supported"

        final dirsToCreate =
            directories ? directories.collect { pathData.directory + ( pathData.directory.endsWith( '/' ) || it.startsWith( '/' ) ? it : "/$it" ) } :
                          [ pathData.directory ]

        sshexec( "scp://${ pathData.username }:${ pathData.password }@${ pathData.host }:~",
                 "mkdir -p ${ dirsToCreate.collect{ it.contains( ' ' ) ? "'$it'" : it }.join( ' ' )}",
                 true,
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
        def pathData  = netBean().parseNetworkPath( remotePath )
        assert 'ftp' == pathData.protocol
        verifyBean().notNullOrEmpty( pathData.username, pathData.password, pathData.host, pathData.directory )

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/ftp.html
         */
        new AntBuilder().ftp( action    : 'put',
                              server    : pathData.host,
                              userid    : pathData.username,
                              password  : pathData.password,
                              remotedir : pathData.directory,
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
        final pathData = netBean().parseNetworkPath( remotePath )
        assert 'scp'  == pathData.protocol

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/scp.html
         */

        final localDestination  = file.canonicalPath
        final authMap           = sshAuthArguments( pathData.password )
        final remoteDestination = "${ authMap.username ?: pathData.username }${ authMap.password ? ':' + authMap.password : '' }@${ pathData.host }:${ pathData.directory }"

        final Map<String,?> arguments = grepMap(( Map<String,?> ) [
            file                 : isDownload ? remoteDestination : localDestination,
            todir                : isDownload ? localDestination  : remoteDestination,
            port                 : pathData.port,
            verbose              : verbose,
            sftp                 : true,
            preserveLastModified : true,
            trust                : true ] + authMap )

        if ( isDownload ){ log.info( "Downloading [scp://${ pathData.host }:${ pathData.directory }] to [$localDestination]" )}
        else             { log.info( "Uploading [$localDestination] to [scp://${ pathData.host }:${ pathData.directory }]"   )}

        new AntBuilder().scp( arguments )
    }


    /**
     * Retrieves a {@code Map} of arguments to be used for ssh-based authentication.
     *
     * @param authData data to use for authentication,
     *        can be either a password, a keyfile path, or a combination of "keyfile___passphrase".
     * @return {@code Map} of arguments to be used for ssh-based authentication
     */
    @Requires({ authData })
    @Ensures ({ result })
    private static Map<String,?> sshAuthArguments( String authData )
    {
        if ( authData == '<settings>' )
        {   // http://maven.apache.org/settings.html
            final  server = ThreadLocals.get( MavenSession ).settings.servers.find { it.username || it.privateKey }
            assert server, "No 'settings.xml' server contains a username or a private key"
            return grepMap ([ username   : server.username,
                              password   : server.password,
                              keyfile    : server.privateKey,
                              passphrase : server.passphrase ])
        }

        if ( new File( authData ).file )
        {
            return [ keyfile : authData ]
        }

        if ( authData.contains( '___' ) && ( ! authData.with { startsWith( '___' ) || endsWith( '___' ) } ))
        {
            def ( String keyfile, String passphrase ) = authData.findAll( /^(.+?)___(.+?)$/ ){ it[ 1, 2 ] }[ 0 ]

            if ( new File( keyfile ).file )
            {
                return [ keyfile : keyfile, passphrase : passphrase ]
            }
        }

        [ password : authData ]
    }
}
