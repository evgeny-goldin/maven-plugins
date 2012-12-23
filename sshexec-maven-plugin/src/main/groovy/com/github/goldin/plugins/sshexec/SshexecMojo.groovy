package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.NetworkUtils
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
    public String[] commands

    @MojoParameter( required = false )
    public String commandDelimitersRegex = '\n|,|;'

    /**
     * Retrieves all execution commands
     * @return all execution commands
     */
    @SuppressWarnings( 'UseCollectMany' )
    private List<String> commands ()
    {
        List<String> commands = general().list( this.commands, this.command )*.split( commandDelimitersRegex ).flatten()*.trim().grep().
                                collect { String command -> [( echoCommands ? "echo Running [${ command.replace( '`', '\\`' ) }]:" : '' ), command ] }.
                                flatten()

        ([ echoPwd ? 'echo Current directory is [`pwd`]' : '' ] + commands ).grep()
    }


    @Override
    void doExecute()
    {
        NetworkUtils.sshexec( location, commands(), verbose )
    }
}
