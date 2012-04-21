package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.jcraft.jsch.JSch
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * MOJO that executes "sshexec"
 *
 * See
 * http://ant.apache.org/manual/Tasks/sshexec.html
 */
@MojoGoal( 'sshexec' )
@MojoPhase( 'install' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class SshexecMojo extends BaseGroovyMojo
{
    /**
     * Server location in format:
     * <protocol>://<user>:<password>@<host>:<directory>
     */
    @MojoParameter( required = true )
    public String location

    @MojoParameter( required = false )
    public boolean verbose = false

    @MojoParameter( required = false )
    public boolean echoPwd = true

    @MojoParameter( required = false )
    public boolean echoCommands = false

    @MojoParameter( required = false )
    public String command

    @MojoParameter( required = false )
    public String commandsShellSeparator = '; '

    @MojoParameter( required = false )
    public String[] commands

    @MojoParameter( required = false )
    public File   keyfile

    @MojoParameter( required = false )
    public String passphrase = ''


    /**
     * Retrieves all execution commands
     * @return all execution commands
     */
    @SuppressWarnings( 'UseCollectMany' )
    private String[] commands ()
    {
        List<String> commands = general().list( this.commands, this.command )
        commands              = commands*.split( /,|;/ ).flatten()*.trim().grep().
                                collect { String command -> [( echoCommands ? "echo Running [${ command.replace( '`', '\\`' ) }]:" : '' ), command ] }.
                                flatten()

        ([ echoPwd ? 'echo Current directory is [`pwd`]' : '' ] + commands ).flatten().grep()
    }


    @Override
    void doExecute()
    {
        Map<String, String> data      = net().parseNetworkPath( location )
        String              username  = data[ 'username' ]
        String              password  = data[ 'password' ]
        String              host      = data[ 'host' ]
        String              directory = data[ 'directory' ]

        long   t        = System.currentTimeMillis()
        String command  = [ "cd $directory", *commands() ].join( commandsShellSeparator )

        log.info( "==> Running sshexec [$command] on [$host:$directory], " +
                  ( keyfile ? "key based authentication with [$keyfile.canonicalPath] private key" :
                              'password based authentication' ))

        /**
         * http://evgeny-goldin.org/youtrack/issue/pl-334:
         * Multiple executions - if one is "<verbose>true</verbose>" all following are verbose as well
         *
         * JSch keeps its logger in a static variable: {@link JSch#logger}
         */
        JSch.logger = JSch.DEVNULL

        if ( keyfile )
        {   /**
             * Key based authentication
             */
            new AntBuilder().sshexec( command     : command,
                                      host        : host,
                                      username    : username,
                                      keyfile     : keyfile.canonicalPath,
                                      passphrase  : passphrase,
                                      verbose     : verbose,
                                      trust       : true,
                                      failonerror : true )
        }
        else
        {   /**
             * Password based authentication
             */
            new AntBuilder().sshexec( command     : command,
                                      host        : host,
                                      username    : username,
                                      password    : password,
                                      verbose     : verbose,
                                      trust       : true,
                                      failonerror : true )
        }

        log.info( "==> Sshexec [$command] run on [$host:$directory] ([${ System.currentTimeMillis() - t }] ms)" )
    }
}
