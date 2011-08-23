package com.goldin.plugins.about

import com.goldin.gcommons.beans.ExecOption
import com.goldin.plugins.common.BaseGroovyMojo
import java.text.SimpleDateFormat
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import static com.goldin.plugins.common.GMojoUtils.*

/**
 * Updates files specified with "about" build metadata
 */
@MojoGoal( 'create-about' )
@MojoPhase( 'package' )
class AboutMojo extends BaseGroovyMojo
{
    public static final String SEPARATOR = '|==============================================================================='

    @MojoParameter
    public String  prefix = 'META-INF'

    @MojoParameter ( defaultValue = 'about-${project.groupId}-${project.artifactId}-${project.version}.txt' )
    public String  fileName

    @MojoParameter
    public boolean updateArchives = true

    @MojoParameter
    public  String addContent = ''
    private String evaluateAddContent()
    {
        addContent.trim().with { ( startsWith( '{{' ) && endsWith( '}}' )) ? eval(( String ) delegate, String ) : delegate }
    }

    @MojoParameter
    public boolean failOnError = true

    @MojoParameter
    public boolean failIfNotFound = true

    @MojoParameter
    public boolean dumpSCM    = true

    @MojoParameter
    public boolean dumpEnv    = false

    @MojoParameter
    public boolean dumpSystem = false

    @MojoParameter
    public boolean dumpMaven  = false

    @MojoParameter
    public boolean dumpPaths  = false

    @MojoParameter
    public boolean dumpDependencies = false

    @MojoParameter
    public String  endOfLine   = 'windows'

    @MojoParameter ( defaultValue = '${project.build.directory}' )
    public File    directory

    @MojoParameter
    public String  include = '*.jar'

    @MojoParameter
    public String  exclude

    private env       = System.getenv()
    private isWindows = System.getProperty( 'os.name' ).toLowerCase().contains( 'windows' )


    private String padLines ( String s, int padWidth, List l = null )
    {
        List<String> lines = (( s != null ) ? s.readLines() : l )

        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * padWidth ) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }


    private String exec ( String command, File directory = basedir )
    {
        generalBean().executeWithResult( command, ( isWindows ? ExecOption.CommonsExec : ExecOption.Runtime ), -1, directory )
    }


    private String find ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    private String find ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: '' }
    private String sort ( Map<?,?> map )
    {
        def maxKey = maxKeyLength( map ) + 3
        map.sort().collect { key, value -> " [$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    /**
     * Retrieves result of running "mvn dependency:tree" for the current project.
     *
     * @return Result of running "mvn dependency:tree" for the current project.
     */
    private String dependencyTree()
    {
        String mvnHome = env[ 'M2_HOME' ]
        assert mvnHome, "'M2_HOME' environment variable is not defined"

        File mvnHomeFile = new File( mvnHome )
        mvnHomeFile.with{ assert isDirectory() && new File(( File ) delegate, 'bin' ).isDirectory() }

        def mvn = new File( mvnHomeFile, 'bin/mvn' + ( isWindows ? '.bat' : '' )).canonicalPath

        exec( "$mvn -B -f ${ new File( basedir, 'pom.xml' ).canonicalPath } " +
              "org.apache.maven.plugins:maven-dependency-plugin:2.3:tree",
              basedir ).replace( '[INFO] ', '' ).
                        replaceAll( /(?s)^.+?@.+?---/,              '' ). // Removing Maven 3 header
                        replaceAll( /(?s)^.+\[dependency:tree.+?]/, '' ). // Removing Maven 2 header
                        replaceAll( /(?s)----+.+$/,                 '' ). // Removing footer
                        trim()
    }


    String jenkinsContent()
    {
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project

        """
        $SEPARATOR
        | Jenkins Info
        $SEPARATOR
        | Server        : [${ env[ 'JENKINS_URL' ] }]
        | Job           : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log           : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String hudsonContent()
    {
        // http://weblogs.java.net/blog/johnsmart/archive/2008/03/using_hudson_en.html

        """
        $SEPARATOR
        | Hudson Info
        $SEPARATOR
        | Server        : [${ env[ 'HUDSON_URL' ] }]
        | Job           : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log           : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String teamcityContent()
    {
        // http://confluence.jetbrains.net/display/TCD65/Predefined+Build+Parameters

        """
        $SEPARATOR
        | TeamCity Info
        $SEPARATOR
        | Project Name  : [${ env[ 'TEAMCITY_PROJECT_NAME' ] }]
        | Build Config  : [${ env[ 'TEAMCITY_BUILDCONF_NAME' ] }]
        | Build Number  : [${ env[ 'BUILD_NUMBER' ] }]"""
    }


    String serverContent()
    {
        ( env[ 'JENKINS_URL'      ] ? jenkinsContent () :
          env[ 'HUDSON_URL'       ] ? hudsonContent  () :
          env[ 'TEAMCITY_VERSION' ] ? teamcityContent() :
                                      '' )
    }


    String buildContent ()
    {
        def props  = System.properties
        def format = new SimpleDateFormat( "dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:'GMT'ZZZZZZ)", Locale.ENGLISH )

        """
        $SEPARATOR
        | Build Info
        $SEPARATOR
        | Host          : [${ env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] ?: exec( 'hostname' ) ?: '' }]
        | Build Time    : Started         - [${ format.format( session.startTime ) }]
        | Build Time    : "About" created - [${ format.format( new Date())         }]
        | User          : [${ props[ 'user.name' ] }]
        | ${ dumpPaths ? 'Directory     : [' + props[ 'user.dir' ] + ']': '' }
        | Java          : [${ props[ 'java.version' ] }][${ props[ 'java.vm.vendor' ] }]${ dumpPaths ? '[' + props[ 'java.home' ] + ']' : '' }[${ props[ 'java.vm.name' ] }]
        | OS            : [${ props[ 'os.name' ] }][${ props[ 'os.arch' ] }][${ props[ 'os.version' ] }]
        $SEPARATOR
        | Maven Info
        $SEPARATOR
        | ${ dumpPaths ? 'M2_HOME       : [' + env[ 'M2_HOME' ] + ']' : '' }
        | MAVEN_OPTS    : [${ env[ 'MAVEN_OPTS' ] ?: '' }]
        | Version       : [${ mavenVersion() }]
        | Project       : [${ dumpPaths ? project.toString() : project.toString().replaceAll( /\s+@.+/, '' )}]
        | Goals         : $session.goals
        | ${ dumpPaths ? 'Basedir       : [' + basedir.canonicalPath + ']': '' }
        | Name          : ${ ( project.name.startsWith( '[' ) ? '' : '[' ) + project.name + ( project.name.endsWith( ']' ) ? '' : ']' ) }
        | Coordinates   : [$project.groupId:$project.artifactId:$project.version]
        | ${ dumpDependencies ? 'Dependencies  : [' + padLines( dependencyTree(), ' Dependencies  : ['.size()) + ']' : '' }"""
    }


    String optionalContent()
    {
        def props = System.properties

        section( dumpMaven,                    'Maven Properties'      ) { sort( project.properties ) } +
        section( addContent.trim() as boolean, 'User Content'          ) { evaluateAddContent() }       +
        section( dumpSystem,                   'System Properties'     ) { sort( props ) }              +
        section( dumpEnv,                      'Environment Variables' ) { sort( env   ) }
    }


    @Requires({ title && content })
    @Ensures({ result })
    String section ( boolean condition, String title, Closure content )
    {
        ( condition ?

        """
        $SEPARATOR
        | $title
        $SEPARATOR
        |${ content() }""" :

        '' )
    }


    String scmContent()
    {
        if ( ! dumpSCM ) { return '' }

        File   svnDir           = new File( basedir, '.svn' )
        String svnVersion       = null
        String svnStatus        = null
        String gitVersion       = null
        String gitStatusCommand = null
        String gitStatus        = null

        /**
         * Trying SVN
         */

        if ( svnDir.isDirectory())
        {
            svnVersion = exec( 'svn --version' )
            if ( svnVersion.contains( 'svn, version' ))
            {
                svnStatus = exec( "svn status $basedir.canonicalPath" )
                if (( ! svnStatus.contains( 'is not a working copy' )) &&
                    ( ! svnStatus.contains( 'containing working copy admin area is missing' )))
                {
                    return svnContent( svnStatus )
                }
            }
        }

        /**
         * Trying Git
         */

        gitVersion = exec( 'git --version' )

        if ( gitVersion.contains( 'git version' ))
        {
            gitStatus = exec( 'git status' )

            if ( ! gitStatus.contains( 'fatal: Not a git repository' ))
            {
                return gitContent( gitStatus )
            }
        }

        """
        $SEPARATOR
        | SCM Info
        $SEPARATOR
        | Unsupported SCM system: either project is not managed by SVN/Git or corresponding command-line clients are not available.
        | Tried SVN:
        | ~~~~~~~~~~
        | [$svnDir.canonicalPath] - ${ svnDir.isDirectory() ? 'found' : 'not found' }
        | ${ svnVersion ? '"svn --version" returned [' + svnVersion + ']'                           : '' }
        | ${ svnStatus  ? '"svn status ' + basedir.canonicalPath + '" returned [' + svnStatus + ']' : '' }
        | Tried Git:
        | ~~~~~~~~~~
        | ${ gitVersion ? '"git --version" returned [' + gitVersion + ']'                            : '' }
        | ${ gitStatus  ? '"' + gitStatusCommand + '" returned [' + gitStatus + ']'                  : '' }"""
    }


    String svnContent( String svnStatus )
    {
        List<String> svnInfo     = exec( "svn info ${basedir.canonicalPath}"      ).readLines()
        List<String> commitLines = exec( "svn log  ${basedir.canonicalPath} -l 1" ).readLines().findAll { it }
        String       commit      = commitLines[ 1 ]

        """
        $SEPARATOR
        | SVN Info
        $SEPARATOR
        | Repository     : [${ find( 'URL:',      svnInfo )}]
        | Revision       : [${ find( 'Revision:', svnInfo )}]
        | Status         : [${ padLines( svnStatus, ' Status         : ['.size()) }]
        | Last Commit    : [$commit]
        | Commit Date    : [${ commit.split( '\\|' )[ 2 ].trim() }]
        | Commit Author  : [${ commit.split( '\\|' )[ 1 ].trim() }]
        | Commit Message : [${ padLines( null, ' Commit Message : ['.size(), commitLines[ 2 .. -2 ]*.trim()) }]"""
    }


    String gitContent( String gitStatus )
    {
        List<String> gitLog = exec( 'git log -1' ).readLines().findAll { it }

        """
        $SEPARATOR
        | Git Info
        $SEPARATOR
        | Repositories   : [${ padLines( exec( 'git remote -v' ), ' Repositories   : ['.size()) }]
        | Branch         : [${ find( '# On branch', 'git status' ) }]
        | Git Status     : [${ padLines( gitStatus, ' Git Status     : ['.size()) }]
        | Last Commit    : [${ find( 'commit',      gitLog )}]
        | Commit Date    : [${ find( 'Date:',       gitLog )}]
        | Commit Author  : [${ find( 'Author:',     gitLog )}]
        | Commit Message : [${ padLines( null, ' Commit Message : ['.size(), gitLog[ 3 .. -1 ]*.trim()) }]"""
    }


    String allContent()
    {
        ( ' Generated by http://evgeny-goldin.com/wiki/Maven-about-plugin\n' +
          serverContent()   +
          scmContent()      +
          buildContent()    +
          optionalContent() + '\n' +
          SEPARATOR ).
        stripMargin().readLines()*.replaceAll( /\s+$/, '' ).findAll { it }. // Deleting empty lines
        join(( 'windows' == endOfLine ) ? '\r\n' : '\n' )
    }


    @Override
    void doExecute ()
    {
        try
        {
            if ( updateArchives )
            {
                if ( ! directory.directory )
                {
                    assert  ( ! failIfNotFound ), "Directory [$directory.canonicalPath] is not available, consider using <failIfNotFound>"
                    log.warn( "Directory [$directory.canonicalPath] is not available, \"about\" is not created" )
                    return
                }

                def split = { String s -> ( List<String> )( s ? s.split( /,/ ).toList()*.trim().findAll{ it } : null ) }
                def files = fileBean().files( directory, split( include ), split( exclude ), false, false, failIfNotFound )

                if ( files )
                {
                    def tempFile = new File( outputDirectory(), fileName )
                    def prefix   = (( prefix == '/' ) ? '' : prefix )

                    log.info( "Generating \"about\" in [$tempFile.canonicalPath], basedir is [${ basedir.canonicalPath }]" )
                    tempFile.write( allContent())
                    log.info( "Generated  \"about\" in [$tempFile.canonicalPath]" )

                    for ( file in files )
                    {
                        def aboutPath = "$file.canonicalPath/$prefix${ prefix ? '/' : '' }$fileName"

                        log.info( "Adding \"about\" to [$aboutPath]" )
                        fileBean().pack( tempFile.parentFile, file, [ tempFile.name ], null, true, true, true, null, null, prefix )
                        log.info( "Added  \"about\" to [$aboutPath]" )
                    }

                    fileBean().delete( tempFile )
                }
                else
                {
                    log.warn( "No files found in [$directory.canonicalPath] and include/exclude patterns [${ include ?: '' }]/[${ exclude ?: '' }]" )
                }
            }
            else
            {
                File aboutFile = ( File ) new File( fileName ).with{ absolute ? delegate : new File( outputDirectory, fileName )}
                assert ( ! aboutFile.file ) || ( aboutFile.delete()), "Failed to delete old [$aboutFile.canonicalPath]"

                log.info( "Generating \"about\" in [$aboutFile.canonicalPath], basedir is [${ basedir.canonicalPath }]" )
                aboutFile.write( allContent())
                log.info( "Generated  \"about\" in [$aboutFile.canonicalPath]" )
            }
        }
        catch ( e )
        {
            def message = "Failed to create \"about\" file"
            if ( failOnError ) { throw new RuntimeException( message, e ) }
            log.error( message + ':', e )
        }
    }
}

