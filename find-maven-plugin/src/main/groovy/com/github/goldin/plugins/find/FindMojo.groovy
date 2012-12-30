package com.github.goldin.plugins.find

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import com.github.goldin.plugins.common.GMojoUtils
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.gcontracts.annotations.Requires


/**
 * MOJO that finds the file specified and sets the corresponding property
 */
@Mojo( name = 'find', defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])

class FindMojo extends BaseGroovyMojo
{
    @Parameter ( required = true )
    private String file

    @Parameter ( defaultValue = '${project.basedir}', required = false )
    private File startDir

    @Parameter
    private String propertyName

    @Parameter
    private String sysPropertyName

    @Parameter
    private boolean failIfNotFound = true


    /**
     * Goes recursively up from the current directory and checks if any of the
     * files specified as {@code <configuration>} is located. If yes - sets the
     * property specified.
     */
    void doExecute()
    {
        File foundFile = find( startDir )
        if ( foundFile )
        {
            String path = foundFile.canonicalPath

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
    @Requires({ startDir.directory })
    private File find( File startDir )
    {
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
            String propertyValue = project.properties.getProperty( propertyName )

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

        failOrWarn( failIfNotFound,
                    "Failed to find [$file] going up from [$startDir.canonicalPath]. "   +
                    ( propertyName    ? "Maven property [$propertyName] isn't set. "     : '' ) +
                    ( sysPropertyName ? "System property [$sysPropertyName] isn't set. " : '' ))
        null
    }
}
