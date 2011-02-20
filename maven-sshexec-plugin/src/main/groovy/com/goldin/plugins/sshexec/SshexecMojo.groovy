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

    /**
     * The command to run on the remote host
     */
    @MojoParameter( required = true )
    public String command

    public SshexecMojo ()
    {
    }


    @Override
    void doExecute()
    {
        assert location, "<location> is not specified"
        assert command,  "<command> is not specified"

        Map<String, String> data      = GCommons.net().parseNetworkPath( location )
        String              username  = data.username
        String              password  = data.password
        String              host      = data.host
        String              directory = data.directory

        log.info( "Running sshexec [$command] on [$host:$directory]" )

        new AntBuilder().sshexec( command     : "cd $directory $command",
                                  host        : host,
                                  username    : username,
                                  password    : password,
                                  trust       : true,
                                  failonerror : true )

        log.info( "Sshexec [$command] run on [$host:$directory]" )
    }
}
