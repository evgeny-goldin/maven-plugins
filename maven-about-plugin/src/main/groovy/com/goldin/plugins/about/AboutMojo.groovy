package com.goldin.plugins.about

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
    public boolean gitStatusProject = true

    @MojoParameter
    public String  endOfLine   = 'windows'

    @MojoParameter ( defaultValue = '${project.build.directory}' )
    public File    directory

    @MojoParameter
    public String  include = '*.jar'

    @MojoParameter
    public String  exclude

    private env = System.getenv()



    private String padLines ( String s )
    {
        def padWidth = ' Status        : ['.size()
        def lines    = s.readLines()

        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * padWidth ) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }


    private String exec ( String command, File directory = null )
    {
        def process = command.execute(( List ) null, directory )
        ( process.text + process.err.text ).trim()
    }


    private String find ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    private String find ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: '' }
    private String sort ( Map<?,?> map )
    {
        def maxKey = maxKeyLength( map ) + 3
        map.sort().collect { key, value -> "[$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    /**
     * Retrieves result of running "mvn dependency:tree" for the current project.
     *
     * @return Result of running "mvn dependency:tree" for the current project.
     */
    private String dependencyTree()
    {
        def mvnHome = env[ 'M2_HOME' ]
        assert mvnHome, "'M2_HOME' environment variable is not defined"
        verifyBean().directory( new File( mvnHome ))

        def mvn = mvnHome + '/bin/' + ( System.getProperty( 'os.name' ).toLowerCase().contains( 'windows' ) ? 'mvn.bat' :
                                                                                                              'mvn' )

        exec( "$mvn -B dependency:tree", basedir ).replace( '[INFO] ', '' ).
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
        | ${ dumpDependencies ? 'Dependencies  : [' + padLines( dependencyTree()) + ']' : '' }""" +

        addSection( dumpMaven,                    'Maven Properties',      { sort( mavenProperties( props )) } ) +
        addSection( addContent.trim() as boolean, 'User Content',          { evaluateAddContent() }            ) +
        addSection( dumpSystem,                   'System Properties',     { sort( props ) }                   ) +
        addSection( dumpEnv,                      'Environment Variables', { sort( env   ) }                   )
    }


    @Requires({ title && content })
    @Ensures({ result })
    String addSection( boolean condition, String title, Closure content )
    {
        ( condition ?

        """
        $SEPARATOR
        | $title
        $SEPARATOR
        |${ content() }""" :

        '' )
    }


    @Requires({ systemProperties })
    @Ensures({ result })
    Map<String, String> mavenProperties( Properties systemProperties )
    {
        ( Map<String, String> )( project.properties + session.executionProperties + session.userProperties ).findAll
        {
            String key, String value -> ( ! ( key.startsWith( 'env.' ) || systemProperties.containsKey( key )))
        }
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
            gitStatusCommand = "git status" + ( gitStatusProject ? '' : ' ' + basedir.canonicalPath )
            gitStatus        = exec( gitStatusCommand )

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
        def svnInfo = exec( "svn info ${basedir.canonicalPath}"      ).readLines()
        def commit  = exec( "svn log  ${basedir.canonicalPath} -l 1" ).readLines()[ 1 ]

        """
        $SEPARATOR
        | SVN Info
        $SEPARATOR
        | Repository    : [${ find( 'URL:',      svnInfo )}]
        | Revision      : [${ find( 'Revision:', svnInfo )}]
        | Status        : [${ padLines( svnStatus ) }]
        | Last Commit   : [$commit]
        | Commit Date   : [${ commit.split( '\\|' )[ 2 ].trim() }]
        | Commit Author : [${ commit.split( '\\|' )[ 1 ].trim() }]"""
    }


    String gitContent( String gitStatus )
    {
        def gitLog = exec( 'git log -1' ).readLines()

        """
        $SEPARATOR
        | Git Info
        $SEPARATOR
        | Repositories  : [${ padLines( exec( 'git remote -v' )) }]
        | Branch        : [${ find( '# On branch', 'git status' ) }]
        | ${ gitStatusProject ? 'Project' : 'Basedir' } Status: [${ padLines( gitStatus ) }]
        | Last Commit   : [${ find( 'commit',      gitLog )}]
        | Commit Date   : [${ find( 'Date:',       gitLog )}]
        | Commit Author : [${ find( 'Author:',     gitLog )}]"""
    }


    @Override
    void doExecute ()
    {
        try
        {
            if ( ! directory.isDirectory())
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

                log.info( "Generating \"about\" in [$tempFile.canonicalPath] .." )

                tempFile.write(( " Generated by http://evgeny-goldin.com/wiki/Maven-about-plugin\n" +
                                 serverContent() + scmContent() + buildContent() + '\n' + SEPARATOR ).
                               stripMargin().readLines()*.replaceAll( /\s+$/, '' ).findAll { it }. // Deleting empty lines
                               join(( 'windows' == endOfLine ) ? '\r\n' : '\n' ))

                log.info( "Generated  \"about\" in [$tempFile.canonicalPath]" )

                for ( file in files )
                {
                    def aboutPath = "$file.canonicalPath/$prefix${ prefix ? '/' : '' }$fileName"
                    log.info( "Adding \"about\" to [$aboutPath]" )

                    new AntBuilder().zip( destfile : file.canonicalPath,     update : true   ){
                        zipfileset(       file     : tempFile.canonicalPath, prefix : prefix )
                    }

                    log.info( "Added  \"about\" to [$aboutPath]" )
                }

                fileBean().delete( tempFile )
            }
            else
            {
                log.warn( "No files found in [$directory.canonicalPath] and include/exclude patterns [${ include ?: '' }]/[${ exclude ?: '' }]" )
            }
        }
        catch ( e )
        {
            def message = "Failed to add \"about\""
            if ( failOnError ) { throw new RuntimeException( message, e ) }
            log.error( message + ':', e )
        }
    }
}
