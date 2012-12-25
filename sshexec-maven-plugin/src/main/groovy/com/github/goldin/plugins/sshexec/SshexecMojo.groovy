package com.github.goldin.plugins.sshexec

import static com.github.goldin.plugins.common.GMojoUtils.*
import java.util.regex.Pattern
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
            assert outputFile.renameTo( this.outputFile ) ,  \
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
