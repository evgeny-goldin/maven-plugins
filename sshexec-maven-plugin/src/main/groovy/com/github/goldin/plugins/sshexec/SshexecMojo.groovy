package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.NetworkUtils
import org.apache.maven.plugin.MojoExecutionException
import org.gcontracts.annotations.Requires
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
    public boolean echoPwd = false

    @MojoParameter( required = false )
    public boolean echoCommands = false

    @MojoParameter( required = false )
    public String command

    @MojoParameter( required = false )
    public String outputProperty

    @MojoParameter( required = false )
    public File outputFile

    @MojoParameter( required = false )
    public boolean failOnError = true

    @MojoParameter( required = false )
    public String failIfOutput

    @MojoParameter( required = false )
    public String failIfNoOutput

    @MojoParameter( required = false )
    public String[] commands

    @MojoParameter( required = false )
    public String commandsShellSeparator = '; '

    @MojoParameter( required = false )
    public String commandDelimitersRegex = '\n|,|;'

    /**
     * Retrieves all execution commands
     * @return all execution commands
     */
    @SuppressWarnings( 'UseCollectMany' )
    private List<String> commands ()
    {
        List<String> commands = generalBean().list( this.commands, this.command )*.split( commandDelimitersRegex ).flatten()*.trim().grep().
                                collect { String command -> [( echoCommands ? "echo Running [${ command.replace( '`', '\\`' ) }]:" : '' ), command ] }.
                                flatten()

        ([ echoPwd ? 'echo Current directory is [`pwd`]' : '' ] + commands ).grep()
    }


    @Override
    void doExecute()
    {
        final startDirectory = netBean().parseNetworkPath( location ).directory
        final outputFile     = NetworkUtils.sshexec( location,
                                                     [ "cd '$startDirectory'", *commands() ].join( commandsShellSeparator ),
                                                     failOnError,
                                                     verbose )
        processOutputFile( outputFile )
    }


    @Requires({ outputFile.file })
    private void processOutputFile ( File outputFile )
    {
        final outputContent = ( failIfOutput || failIfNoOutput || outputProperty ) ? outputFile.getText( 'UTF-8' ) : null

        if ( failIfOutput )
        {
            for ( badOutput in failIfOutput.tokenize( '|' ))
            {
                if ( outputContent.contains( badOutput ))
                {
                    throw new MojoExecutionException( "Sshexec output [$outputContent] contains [$badOutput]" )
                }
            }
        }

        if ( failIfNoOutput )
        {
            for ( goodOutput in failIfNoOutput.tokenize( '|' ))
            {
                if ( ! outputContent.contains( goodOutput ))
                {
                    throw new MojoExecutionException( "Sshexec output [$outputContent] contains no [$goodOutput]" )
                }
            }
        }

        if ( outputProperty )
        {
            setProperty( outputProperty, outputContent, '', false )
        }

        if ( this.outputFile )
        {
            fileBean().mkdirs( this.outputFile.parentFile )
            assert outputFile.renameTo( this.outputFile ) ,  \
                    "Failed to rename [$outputFile.canonicalPath] to [${ this.outputFile.canonicalPath }]"
        }
    }
}
