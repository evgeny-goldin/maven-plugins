package com.goldin.plugins.about

import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoPhase
import com.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoParameter
import com.goldin.gcommons.GCommons
import java.text.SimpleDateFormat
import com.goldin.plugins.common.GMojoUtils

/**
 * Updates files specified with "about" build metadata
 */
@MojoGoal( 'create-about' )
@MojoPhase( 'package' )
class AboutMojo extends BaseGroovyMojo
{
    @MojoParameter
    public String scm = 'svn'

    @MojoParameter
    public boolean dumpEnv = true

    @MojoParameter
    public boolean dumpSystem = true

    @MojoParameter
    public String endOfLine = 'windows'

    @MojoParameter
    public File directory

    @MojoParameter
    public File file

    @MojoParameter
    public String include
    
    @MojoParameter
    public String exclude


    public void setFile ( File file )
    {
        GCommons.verify().file( file )
        assert file.parentFile, "File [$file.canonicalPath] has no parent directory"

        this.directory = file.parentFile
        this.include   = file.name
    }

    String exec     ( String command )                { command.execute().text }
    String padLines ( String s, int j )               { def lines = s.readLines()
                                                        ( lines ? ( lines[ 0 ] +
                                                                    (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { ( ' ' * j ) + it }.join( '\n' ) :
                                                                                            '' )) :
                                                                    '' )
    }
    String find     ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    String find     ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }.replace( prefix, '' ).trim() }
    String sort     ( Map<String,String> map )        { def maxKey = map.keySet().collect { it.size() }.max()
                                                        map.sort().
                                                            collect { String key, String value ->
                                                                      "[$key]".padRight( maxKey + 3 ) + ":[$value]" }.
                                                            join( '\n' )
    }

    String generalContent()
    {
        def env   = System.getenv()
        def props = System.properties

        """
        |===============================================================================
        | Maven Info
        |===============================================================================
        | M2_HOME       : [${ env[ 'M2_HOME' ]}]
        | Version       : [${ GMojoUtils.mavenVersion() }]
        | Basedir       : [$basedir.canonicalPath]
        | Project       : [$project]
        | Name          : [$project.name]
        | Coordinates   : [$project.groupId:$project.artifactId:$project.version]
        |===============================================================================
        | Build Info
        |===============================================================================
        | Host          : [${ env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] }]
        | Time          : [${ new SimpleDateFormat( "dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:'GMT'ZZZZZZ)", Locale.ENGLISH ).format( new Date()) }]
        | User          : [${ props[ 'user.name' ] }]
        | Directory     : [${ props[ 'user.dir'  ] }]
        | Java          : [${ props[ 'java.version' ] }][${ props[ 'java.vm.vendor' ] }][${ props[ 'java.home' ] }][${ props[ 'java.vm.name' ] }]
        | OS            : [${ props[ 'os.name' ] }][${ props[ 'os.arch' ] }][${ props[ 'os.version' ] }]
        |===============================================================================""" +

        ( dumpSystem ?

        """
        | System Properties
        |===============================================================================
        |${ sort( props ) }
        |===============================================================================
        """ : '' ) +

        ( dumpEnv ?
        
        """
        | Environment Variables
        |===============================================================================
        |${ sort( env ) }
        |===============================================================================
        """ : '' )
    }

    
    String svnContent()
    {
        assert exec( 'svn --version' ), "Failed to run 'svn --version'"
        def svnInfo = exec( "svn info ${basedir.canonicalPath}" ).readLines()
        def commit  = exec( 'svn log -l 1' ).readLines()[ 1 ]

        """
        |===============================================================================
        | SVN Info
        |===============================================================================
        | Repository    : [${ find( 'URL:',      svnInfo )}]
        | Revision      : [${ find( 'Revision:', svnInfo )}]
        | Status        : [${ padLines( exec( 'svn status' ), ' Status        : ['.size() ) }]
        | Last Commit   : [$commit]
        | Commit Date   : [${ commit.split( '\\|' )[ 2 ].trim() }]
        | Commit Author : [${ commit.split( '\\|' )[ 1 ].trim() }]
        """ + generalContent()
    }

    
    String gitContent()
    {
        assert exec( 'git --version' ), "Failed to run 'git --version'"
        def gitLog = exec( 'git log -1' ).readLines()

        """
        |===============================================================================
        | Git Info
        |===============================================================================
        | Repository    : [${ find( 'origin',      'git remote -v' )}]
        | Branch        : [${ find( '# On branch', 'git status' )}]
        | Status        : [${ padLines( exec( 'git status' ), ' Status        : ['.size() ) }]
        | Last Commit   : [${ find( 'commit',      gitLog )}]
        | Commit Date   : [${ find( 'Date:',       gitLog )}]
        | Commit Author : [${ find( 'Author:',     gitLog )}]
        """ + generalContent()
    }


    @Override
    void doExecute ()
    {
        def split    = { String s -> ( s ? s.split( /,/ ).toList()*.trim().findAll{ it } : null ) }
        def files    = GCommons.file().files( directory, split( include ?: '*.jar' ), split( exclude ))
        def tempFile = new File( outputDirectory, "about-${project.groupId}-${project.artifactId}-${project.version}.txt" )
        def content  = (( 'svn' == scm ) ? svnContent() : gitContent()).stripMargin().trim().
                       replaceAll( /\r?\n/, ( 'windows' == endOfLine ) ? '\r\n' : '\n' )
        tempFile.write( content )

        for ( file in files )
        {
            log.info( "Updating [$file.canonicalPath] .." )

            new AntBuilder().zip( destfile: file.canonicalPath,
                                  update  : true ){
                zipfileset( file  : tempFile.canonicalPath,
                            prefix: 'META-INF' )
            }

            log.info( "Updating [$file.canonicalPath] - Done" )
        }
    }
}
