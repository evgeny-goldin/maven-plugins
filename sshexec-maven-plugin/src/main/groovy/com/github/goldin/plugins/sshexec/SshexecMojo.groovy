package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.NetworkUtils
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.gcontracts.annotations.Requires
import java.util.regex.Pattern


/**
 * MOJO executing Ant's "sshexec"
 */
@Mojo( name = 'sshexec', defaultPhase = LifecyclePhase.INSTALL, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])
class SshexecMojo extends BaseGroovyMojo
{
    /**
     * Server location in formats:
     * <protocol>://<user>:<auth>@<host>:<directory>
     * <protocol>://<user>:<auth>@<host>:<port>:<directory>
     */
    @Parameter( required = true )
    private String location

    @Parameter( required = false )
    private boolean verbose = false

    @Parameter( required = false )
    private boolean echoPwd = false

    @Parameter( required = false )
    private boolean echoCommands = false

    @Parameter( required = false )
    private String command

    @Parameter( required = false )
    private String outputProperty

    @Parameter( required = false )
    private File outputFile

    @Parameter( required = false )
    private boolean failOnError = true

    @Parameter( required = false )
    private String failIfOutput

    @Parameter( required = false )
    private String failIfNoOutput

    @Parameter( required = false )
    private String[] commands

    @Parameter( required = false )
    private String commandsShellSeparator = '; '

    @Parameter( required = false )
    private String commandDelimitersRegex = '\n|,|;'

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
        final output         = NetworkUtils.sshexec( location,
                                                     [ "cd '$startDirectory'", *commands() ].join( commandsShellSeparator ),
                                                     failOnError,
                                                     verbose )
        processOutput( output )
    }


    @Requires({ output != null })
    private void processOutput ( String output )
    {
        if ( isMatch( output, failIfOutput, false ))
        {
            throw new MojoExecutionException( "Sshexec output [$output] contains [$failIfOutput]" )
        }

        if ( ! isMatch( output, failIfNoOutput, true ))
        {
            throw new MojoExecutionException( "Sshexec output [$output] contains no [$failIfNoOutput]" )
        }

        if ( outputProperty )
        {
            setProperty( outputProperty, output, '', false )
        }

        if ( this.outputFile )
        {
            write( this.outputFile, output )
        }
    }


    @Requires({ text != null })
    private boolean isMatch( String text, String matcher, boolean defaultValue )
    {
        if ( ! matcher ) { return defaultValue }

        if (( matcher.length() > 2 ) && matcher.with{ startsWith( '/' ) && endsWith( '/' ) })
        {   // Matcher is "/regex pattern/"
            final pattern = verifyBean().notNullOrEmpty( matcher[ 1 .. -2 ] )
            Pattern.compile( pattern ).matcher( text ).find()
        }
        else
        {   // Matcher is "token1|token2"
            matcher.tokenize( '|' ).any { String token -> text.contains( token )}
        }
    }
}
