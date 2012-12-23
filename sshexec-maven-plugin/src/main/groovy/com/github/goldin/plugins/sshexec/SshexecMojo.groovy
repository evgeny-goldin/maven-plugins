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

    @MojoParameter( required = false )
    public String commandDelimitersRegex = '\n|,|;'

    /**
     * Retrieves all execution commands
     * @return all execution commands
     */
    @SuppressWarnings( 'UseCollectMany' )
    private String[] commands ()
    {
        List<String> commands = general().list( this.commands, this.command )*.split( commandDelimitersRegex ).flatten()*.trim().grep().
                                collect { String command -> [( echoCommands ? "echo Running [${ command.replace( '`', '\\`' ) }]:" : '' ), command ] }.
                                flatten()

        ([ echoPwd ? 'echo Current directory is [`pwd`]' : '' ] + commands ).flatten().grep()
    }


    @Override
    void doExecute()
    {
        final data = net().parseNetworkPath( location )
        assert 'scp' == data.protocol

        /**
         * http://evgeny-goldin.org/javadoc/ant/Tasks/sshexec.html
         */

        final Map<String, String> arguments = [
            command     : [ "cd $data.directory", *commands() ].join( commandsShellSeparator ),
            host        : data.host,
            username    : data.username,
            verbose     : verbose,
            trust       : true,
            failonerror : true ] + sshAuthArguments( data.password ) +
                                   ( data.port ? [ port : data.port ] : [:] )

        final t = System.currentTimeMillis()
        log.info( "==> Running sshexec [$command] on [${ data.host }:${ data.directory }]" )
        new AntBuilder().sshexec( arguments )
        log.info( "==> Sshexec [$command] run on [${ data.host }:${ data.directory }] (${ System.currentTimeMillis() - t } ms)" )
    }
}
