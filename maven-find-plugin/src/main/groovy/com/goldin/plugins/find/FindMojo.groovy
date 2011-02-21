package com.goldin.plugins.find

import com.goldin.gcommons.GCommons
import com.goldin.plugins.common.BaseGroovyMojo
import com.goldin.plugins.common.GMojoUtils
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * MOJO that finds the file specified and sets the corresponding property
 */
@MojoGoal( 'find' )
@MojoPhase( 'validate' )
class FindMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = true )
    public String file

    @MojoParameter ( defaultValue = '${project.basedir}', required = false )
    public File startDir

    @MojoParameter
    public String propertyName

    @MojoParameter
    public String sysPropertyName

    @MojoParameter
    public boolean failIfNotFound = true


    FindMojo ()
    {
    }


    /**
     * Goes recursively up from the current directory and checks if any of the
     * files specified as {@code <configuration>} is located. If yes - sets the
     * property specified.
     */
    void doExecute()
    {
        File file = find( startDir )
        if ( file )
        {
            def path = GCommons.verify().exists( file ).canonicalPath

            if ( propertyName )
            {
                GMojoUtils.setProperty( propertyName, path )
            }

            if ( sysPropertyName )
            {
                System.setProperty( sysPropertyName, path )
                log.info( ">> System property [$sysPropertyName] is set to \"$path\"" )
            }
        }
    }


    /**
     * Finds the file requested starting from the directory provided.
     *
     * @param startDir directory to start searching from
     * @return file found or <code>null</code>
     */
    private File find( File startDir )
    {
        GCommons.verify().directory( startDir )

        log.info( "Looking for [$file] starting from [$startDir.canonicalPath]" )

        if ( sysPropertyName )
        {
            String sysPropertyValue = System.getProperty( sysPropertyName )

            if (( sysPropertyValue ) && ( new File( sysPropertyValue ).exists()))
            {
                log.info( "System property [$sysPropertyName] is already set to [$sysPropertyValue]" )
                return new File( sysPropertyValue )
            }
        }

        if ( propertyName )
        {
            String propertyValue = mavenProject.properties.getProperty( propertyName )

            if (( propertyValue ) && ( new File( propertyValue ).exists()))
            {
                log.info( "Maven property [$propertyName] is already set to [$propertyValue]" )
                return new File( propertyValue )
            }
        }

        for ( File currentDir = startDir; ( currentDir ); currentDir = currentDir.parentFile )
        {
            File file = new File( currentDir, file )
            if ( file.exists())
            {
                log.info( "[$file.canonicalPath] found" )
                return file
            }
        }

        String message = "Failed to find [$file] going up from [$startDir.canonicalPath]. "   +
                         ( propertyName    ? "Maven property [$propertyName] isn't set. "     : '' ) +
                         ( sysPropertyName ? "System property [$sysPropertyName] isn't set. " : '' )

        assert ( ! failIfNotFound ), message
        log.warn( message )
        null
    }
}
