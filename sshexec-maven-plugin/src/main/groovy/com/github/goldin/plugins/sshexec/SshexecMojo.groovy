package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.jfrog.maven.annomojo.annotations.MojoThreadSafe


/**
 * MOJO that executes "sshexec"
 *
 * See
 * http://ant.apache.org/manual/Tasks/sshexec.html
 */
@MojoThreadSafe
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
        String              password  = data[ 'password' ] // Can be a private key
        String              host      = data[ 'host' ]
        String              directory = data[ 'directory' ]

        verify().notNullOrEmpty( username, password, host, directory )

        final t         = System.currentTimeMillis()
        final command   = [ "cd $directory", *commands() ].join( commandsShellSeparator )
        final isKeyfile = new File( password ).file

        log.info( "==> Running sshexec [$command] on [$host:$directory], " +
                  ( isKeyfile ? "key based authentication using [$password]" :
                                'password based authentication' ))

        final arguments = [ command     : command,
                            host        : host,
                            username    : username,
                            verbose     : verbose,
                            trust       : true,
                            failonerror : true ] + [ ( isKeyfile ? 'keyfile' : 'password' ) : password ]

        new AntBuilder().sshexec( arguments )
        log.info( "==> Sshexec [$command] run on [$host:$directory] ([${ System.currentTimeMillis() - t }] ms)" )
    }
}
