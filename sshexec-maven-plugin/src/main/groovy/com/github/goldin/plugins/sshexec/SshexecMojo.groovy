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
    public String location

    @Parameter( required = false )
    public boolean verbose = false

    @Parameter( required = false )
    public boolean echoPwd = false

    @Parameter( required = false )
    public boolean echoCommands = false

    @Parameter( required = false )
    public String command

    @Parameter( required = false )
    public String outputProperty

    @Parameter( required = false )
    public File outputFile

    @Parameter( required = false )
    public boolean failOnError = true

    @Parameter( required = false )
    public String failIfOutput

    @Parameter( required = false )
    public String failIfNoOutput

    @Parameter( required = false )
    public String[] commands

    @Parameter( required = false )
    public String commandsShellSeparator = '; '

    @Parameter( required = false )
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
        final outputContent = outputFile.getText( 'UTF-8' )

        if ( isMatch( outputContent, failIfOutput, false ))
        {
            throw new MojoExecutionException( "Sshexec output [$outputContent] contains [$failIfOutput]" )
        }

        if ( ! isMatch( outputContent, failIfNoOutput, true ))
        {
            throw new MojoExecutionException( "Sshexec output [$outputContent] contains no [$failIfNoOutput]" )
        }

        if ( outputProperty )
        {
            setProperty( outputProperty, outputContent, '', false )
        }

        if ( this.outputFile )
        {
            fileBean().mkdirs( this.outputFile.parentFile )
            assert outputFile.renameTo( this.outputFile ),  \
                   "Failed to rename [$outputFile.canonicalPath] to [${ this.outputFile.canonicalPath }]"
        }

        fileBean().delete( outputFile )
    }


    @Requires({ text != null })
    private boolean isMatch( String text, String matcher, boolean defaultValue )
    {
        if ( ! matcher ) { return defaultValue }

        if (( matcher.length() > 2 ) && matcher.with{ startsWith( '/' ) && endsWith( '/' ) })
        {   // Matcher is "/regex pattern/"
            final pattern = verifyBean().notNullOrEmpty( matcher.substring( 1, matcher.length() - 1 ))
            Pattern.compile( pattern ).matcher( text ).find()
        }
        else
        {   // Matcher is "token1|token2"
            matcher.tokenize( '|' ).any { String token -> text.contains( token )}
        }
    }
}
