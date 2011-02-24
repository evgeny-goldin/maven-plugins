package com.goldin.plugins.sshexec

import com.goldin.gcommons.GCommons
import com.goldin.plugins.common.BaseGroovyMojo
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
public class SshexecMojo extends BaseGroovyMojo
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
    public String[] commands


    /**
     * Retrieves all execution commands
     * @return all execution commands
     */
    private String[] commands ()
    {
        String[] commands = GCommons.general().array( this.commands, this.command, String )
        commands          = commands*.split( /,|;/ ).flatten()*.trim().findAll{ it }.
                            collect { String command -> [( echoCommands ? "echo Running [$command]:" : '' ), command ] }.
                            flatten()

        ([ echoPwd ? 'echo Current directory is [`pwd`]' : '' ] + commands ).
            flatten().findAll{ it }
    }


    public SshexecMojo ()
    {
    }


    @Override
    void doExecute()
    {
        Map<String, String> data      = GCommons.net().parseNetworkPath( location )
        String              username  = data[ 'username' ]
        String              password  = data[ 'password' ]
        String              host      = data[ 'host' ]
        String              directory = data[ 'directory' ]

        long   t        = System.currentTimeMillis()
        String command  = [ "cd $directory", *commands() ].join( '; ' )

        log.info( "==> Running sshexec [$command] on [$host:$directory]" )

        new AntBuilder().sshexec( command     : command,
                                  host        : host,
                                  username    : username,
                                  password    : password,
                                  trust       : true,
                                  verbose     : verbose,
                                  failonerror : true )

        log.info( "==> Sshexec [$command] run on [$host:$directory] ([${ System.currentTimeMillis() - t }] ms)" )
    }
}
