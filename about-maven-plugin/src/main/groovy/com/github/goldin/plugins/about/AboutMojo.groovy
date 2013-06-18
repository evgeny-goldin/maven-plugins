package com.github.goldin.plugins.about

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.gcommons.beans.ExecOption
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import java.text.SimpleDateFormat


/**
 * Updates files specified with "about" build metadata
 */
@Mojo ( name = 'create-about', defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])

class AboutMojo extends BaseGroovyMojo
{
    public static final String SEPARATOR = '|==============================================================================='

    @Parameter
    private boolean updateArchives = true

    @Parameter
    private String  prefix = 'META-INF'

    @Parameter ( defaultValue = 'about-${project.groupId}-${project.artifactId}-${project.version}.txt' )
    private String  fileName

    // http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.apache.maven.plugins%22%20AND%20a%3A%22maven-dependency-plugin%22
    @Parameter ( defaultValue = '2.8' )
    private String  mavenDependencyPluginVersion

    @Parameter
    private String  mavenCommandLine

    @Parameter
    private boolean dumpSCM    = true

    @Parameter
    private boolean dumpMaven  = false

    @Parameter
    private boolean dumpEnv    = false

    @Parameter
    private boolean dumpSystem = false

    @Parameter
    private boolean dumpPaths  = false

    @Parameter
    private boolean dumpDependencies = false

    @Parameter
    private String addContent = ''
    private String evaluateAddContent()
    {
        addContent.trim().with { ( startsWith( '{{' ) && endsWith( '}}' )) ? eval(( String ) delegate, String ) : delegate }
    }

    @Parameter
    private String  endOfLine   = 'windows'

    @Parameter ( defaultValue = '${project.build.directory}' )
    private File    directory

    @Parameter
    private String  include = '*.jar'

    @Parameter
    private String  exclude

    @Parameter
    private boolean failOnError = true

    @Parameter
    private boolean failIfNotFound = true

    private env = System.getenv()


    @Requires({ ( s != null ) && prefix })
    @Ensures({ result != null })
    private String padLines ( String s, String prefix )
    {
        List<String> lines = s.readLines()
        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * prefix.size()) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }


    private String find ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    private String find ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: '' }
    private String sort ( Map<?,?> map )
    {
        final maxKey = maxKeyLength( map ) + 3
        map.sort().collect { key, value -> " [$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    /**
     * Retrieves result of running "mvn dependency:tree" for the current project.
     *
     * @return Result of running "mvn dependency:tree" for the current project.
     */
    @SuppressWarnings([ 'GroovyAccessibility' ])
    @Ensures({ result })
    private String dependencyTree()
    {
        if ( project.collectedProjects )
        {
            return 'Aggregate project, no dependencies shown'
        }

        final coordinates  = "${project.groupId}:${project.artifactId}:${project.packaging}:${project.version}"
        final plugin       = "maven-dependency-plugin:$mavenDependencyPluginVersion:tree"
        final settingsFile = ( File ) [ session.settings.request.userSettingsFile,
                                        session.settings.request.globalSettingsFile ].find { File f -> f.file }
        final isWindows    = System.getProperty( 'os.name', '' ).toLowerCase().contains( 'windows' )
        final mvnHome      = env[ 'M2_HOME' ]
        final mvn          = mvnHome ? new File( mvnHome, 'bin' ).canonicalPath + '/mvn' + ( isWindows ? '.bat' : '' ) :
                                       'mvn'
        final mavenRepo    = System.getProperty( 'maven.repo.local', session.localRepository?.basedir )
        final command      = "$mvn -e -B -f \"${ project.file.canonicalPath }\" org.apache.maven.plugins:$plugin" +
                             ( settingsFile.file ? " -s \"$settingsFile.canonicalPath\"" : '' ) +
                             ( mavenRepo         ? " -Dmaven.repo.local=$mavenRepo"      : '' ) +
                             ( mavenCommandLine  ? " $mavenCommandLine"                  : '' )

        log.info( "Running [$command]" )

        final mdt = exec( command )

        assert [ plugin, coordinates ].every { mdt.contains( it ) }, \
               "Failed to run [$plugin] - data received doesn't contain enough information: [$mdt]"

        final mdtStripped = mdt.replace( '[INFO] ',                         '' ).
                                replace( '[WARNING] ',                      '' ).
                                replaceAll( /(?s)^.+?@.+ ---/,              '' ). // Removing Maven 3 header
                                replaceAll( /(?s)^.+\[dependency:tree.+?]/, '' ). // Removing Maven 2 header
                                replaceAll( /(?m)Downloading: .+$/,         '' ). // Removing Maven 3 download progress indicator
                                replaceAll( /(?m)Downloaded: .+$/,          '' ). // Removing Maven 3 download progress indicator
                                replaceAll( /(?s)----+.+$/,                 '' ). // Removing footer
                                trim()

        assert mdtStripped.startsWith( coordinates ), \
               "Failed to run [$plugin] - cleaned up data should start with [$coordinates]: [$mdtStripped]"

        project.artifacts.each {
            Artifact a ->
            if ( ! a.groupId.startsWith( IVY_PREFIX ))
            {
                "$a.groupId:$a.artifactId".with {
                    assert mdtStripped.contains(( String ) delegate ), \
                    "Failed to run [$plugin] - cleaned up data should contain [$delegate]: [$mdtStripped]"
                }
            }
        }

        mdtStripped
    }


    String jenkinsContent()
    {
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project

        """
        $SEPARATOR
        | Jenkins Info
        $SEPARATOR
        | Server         : [${ env[ 'JENKINS_URL' ] }]
        | Job            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String hudsonContent()
    {
        // http://weblogs.java.net/blog/johnsmart/archive/2008/03/using_hudson_en.html

        """
        $SEPARATOR
        | Hudson Info
        $SEPARATOR
        | Server         : [${ env[ 'HUDSON_URL' ] }]
        | Job            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String teamcityContent()
    {
        // http://confluence.jetbrains.net/display/TCD65/Predefined+Build+Parameters
        // http://confluence.jetbrains.net/display/TCD7/Predefined+Build+Parameters

        final urlMessage  = 'Define \'TEAMCITY_URL\' environment variable and make sure \'-Dteamcity.build.id\' specified when job starts'
        final buildId     = System.getProperty( 'teamcity.build.id' )
        final teamCityUrl = ( env[ 'TEAMCITY_URL' ]?.replaceAll( /(?<!\\|\/)(\\|\/)*$/, '/' )       ?: '' )
        final buildUrl    = ( buildId && teamCityUrl ? "${teamCityUrl}viewLog.html?buildId=$buildId" : '' )
        final logUrl      = ( buildUrl               ? "$buildUrl&tab=buildLog"                      : '' )

        """
        $SEPARATOR
        | TeamCity Info
        $SEPARATOR
        | Server         : [${ teamCityUrl ?: urlMessage }]
        | Job            : [${ buildUrl    ?: urlMessage }]
        | Log            : [${ logUrl      ?: urlMessage }]
        | Server Version : [${ env[ 'TEAMCITY_VERSION' ] }]
        | Project        : [${ env[ 'TEAMCITY_PROJECT_NAME' ] }]
        | Configuration  : [${ env[ 'TEAMCITY_BUILDCONF_NAME' ] }]
        | Build Number   : [${ env[ 'BUILD_NUMBER' ] }]"""
    }


    String serverContent()
    {
        ( env[ 'JENKINS_URL'      ] ? jenkinsContent () :
          env[ 'HUDSON_URL'       ] ? hudsonContent  () :
          env[ 'TEAMCITY_VERSION' ] ? teamcityContent() :
                                      '' )
    }


    @SuppressWarnings( 'LineLength' )
    String buildContent ()
    {
        final props  = System.properties
        final format = new SimpleDateFormat( "dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:'GMT'ZZZZZZ)", Locale.ENGLISH )

        """
        $SEPARATOR
        | Build Info
        $SEPARATOR
        | Host           : [${ env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] ?: exec( 'hostname' ) ?: '' }]
        | Build Time     : Started         - [${ format.format( session.startTime ) }]
        | Build Time     : "About" created - [${ format.format( new Date())         }]
        | User           : [${ props[ 'user.name' ] }]
        | ${ dumpPaths ? 'Directory      : [' + props[ 'user.dir' ] + ']': '' }
        | Java           : [${ props[ 'java.version' ] }][${ props[ 'java.vm.vendor' ] }]${ dumpPaths ? '[' + props[ 'java.home' ] + ']' : '' }[${ props[ 'java.vm.name' ] }]
        | OS             : [${ props[ 'os.name' ] }][${ props[ 'os.arch' ] }][${ props[ 'os.version' ] }]
        $SEPARATOR
        | Maven Info
        $SEPARATOR
        | ${ dumpPaths ? 'M2_HOME        : [' + env[ 'M2_HOME' ] + ']' : '' }
        | MAVEN_OPTS     : [${ env[ 'MAVEN_OPTS' ] ?: '' }]
        | Version        : [${ mavenVersion() }]
        | Project        : [${ dumpPaths ? project.toString() : project.toString().replaceAll( /\s+@.+/, '' )}]
        | Goals          : $session.goals
        | ${ dumpPaths ? 'Basedir        : [' + basedir.canonicalPath + ']': '' }
        | Name           : ${ ( project.name.startsWith( '[' ) ? '' : '[' ) + project.name + ( project.name.endsWith( ']' ) ? '' : ']' ) }
        | Coordinates    : [$project.groupId:$project.artifactId:$project.version]
        | ${ dumpDependencies ? 'Dependencies   : [' + padLines( dependencyTree(), ' Dependencies   : [' ) + ']' : '' }"""
    }


    String optionalContent()
    {
        final props = System.properties

        section( dumpMaven,                    'Maven Properties'      ) { sort( project.properties ) } +
        section( addContent.trim() as boolean, 'User Content'          ) { evaluateAddContent() }       +
        section( dumpSystem,                   'System Properties'     ) { sort( props ) }              +
        section( dumpEnv,                      'Environment Variables' ) { sort( env   ) }
    }


    @Requires({ title && content })
    @Ensures({ result != null })
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

        File   svnDir     = new File( basedir, '.svn' )
        String svnVersion = null
        String svnStatus  = null
        String gitVersion = null
        String gitStatus  = null

        /**
         * Trying SVN
         */

        if ( svnDir.directory )
        {
            svnVersion = exec( 'svn --version', basedir, false )
            if ( svnVersion.toLowerCase().contains( 'svn, version' ))
            {
                svnStatus = exec( "svn status $basedir.canonicalPath", basedir, false, false )
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

        gitVersion = exec( 'git --version', basedir, false )

        if ( gitVersion.contains( 'git version' ))
        {
            gitStatus = exec( 'git status', basedir, false )

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
        | [$svnDir.canonicalPath] - ${ svnDir.directory ? 'exists' : 'does not exist' }
        | ${ svnVersion ? '"svn --version" returned [' + svnVersion + ']'                           : '' }
        | ${ svnStatus  ? '"svn status ' + basedir.canonicalPath + '" returned [' + svnStatus + ']' : '' }
        | Tried Git:
        | ~~~~~~~~~~
        | ${ gitVersion ? '"git --version" returned [' + gitVersion + ']'                            : '' }
        | ${ gitStatus  ? '"git status" returned [' + gitStatus + ']'                                : '' }"""
    }


    String svnContent( String svnStatus )
    {
        /**
         * Path: .
         * URL: http://server/path/project
         * Repository Root: http://server
         * Repository UUID: 3b0d414a-98df-144e-9520-49725f2e85eb
         * Revision: 39134
         * Node Kind: directory
         * Schedule: normal
         * Last Changed Author: Evgeny
         * Last Changed Rev: 39087
         * Last Changed Date: 2011-08-24 09:28:06 +0300 (Wed, 24 Aug 2011)
         */

        List<String> svnInfo = exec( "svn info ${basedir.canonicalPath}", basedir, true, true, ExecOption.Runtime, 2 ).readLines()

        /**
         * ------------------------------------------------------------------------
         * r39087 | Evgeny | 2011-08-24 09:28:06 +0300 (Wed, 24 Aug 2011) | 1 line
         *
         * About removed
         * ------------------------------------------------------------------------
         */

        List<String> commitLines = exec( "svn log  ${basedir.canonicalPath} -l 1", basedir, true, true, ExecOption.Runtime, 3 ).readLines().grep()

        assert [ commitLines[ 0 ], commitLines[ -1 ]].each { it.with { startsWith( '---' ) && endsWith( '---' ) }}, \
               "Unknown commit format:\n$commitLines"

        String       commit        = commitLines[ 1 ]
        List<String> commitMessage = ( commitLines.size() > 3 ) ? commitLines[ 2 .. -2 ]*.trim() : []

        """
        $SEPARATOR
        | SVN Info
        $SEPARATOR
        | Repository     : [${ find( 'URL:',      svnInfo )}]
        | Revision       : [${ find( 'Revision:', svnInfo )}]
        | Status         : [${ padLines( svnStatus, ' Status         : [' ) }]
        | Commit         : [$commit]
        | Commit Date    : [${ split( commit, '|' )[ 2 ].trim() }]
        | Commit Author  : [${ split( commit, '|' )[ 1 ].trim() }]
        | Commit Message : [${ padLines( commitMessage.join( '\n' ), ' Commit Message : [' ) }]"""
    }


    String gitContent( String gitStatus )
    {
        /**
         * http://schacon.github.com/git/git-log.html
         * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
         * dc38954
         * dc389541c4aa7f72f07f11236b1c632a919de61c
         * Fri, 28 Oct 2011 15:40:03 +0200
         * Evgeny Goldin
         * evgenyg@gmail.com
         * <version>0.2.3.5-SNAPSHOT</version>
         * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
         */
        List<String> log = exec( 'git log -1 --format=format:%h%n%H%n%cD%n%cN%n%ce%n%B', basedir, true, true, ExecOption.Runtime, 5 ).readLines()*.trim()

        """
        $SEPARATOR
        | Git Info
        $SEPARATOR
        | Repositories   : [${ padLines( exec( 'git remote -v' ), ' Repositories   : [' ) }]
        | Branch         : [${ find( '# On branch', 'git status' ) }]
        | Git Status     : [${ padLines( gitStatus, ' Git Status     : [' ) }]
        | Commit         : [${ log[ 0 ] }][${ log[ 1 ] }]
        | Commit Date    : [${ log[ 2 ] }]
        | Commit Author  : [${ log[ 3 ] } <${ log[ 4 ] }>]
        | Commit Message : [${ log.size() > 5 ? padLines( log[ 5 .. -1 ].join( '\n' ), ' Commit Message : [' ) : '' }]"""
    }


    String allContent()
    {
        final version = properties( 'META-INF/maven/com.github.goldin/about-maven-plugin/pom.properties', AboutMojo.classLoader ).
                        getProperty( 'version', '' )

        ( " Created with http://evgeny-goldin.com/wiki/Maven-about-plugin${ version ? ', version "' + version + '"' : '' }\n" +
          serverContent()   +
          scmContent()      +
          buildContent()    +
          optionalContent() + '\n' +
          SEPARATOR ).
        stripMargin().readLines()*.replaceAll( /\s+$/, '' ).grep(). // Deleting empty lines
        join(( 'windows' == endOfLine ) ? '\r\n' : '\n' )
    }


    @Requires({ aboutFile })
    @Ensures ({ result == aboutFile })
    File writeAboutFile( File aboutFile )
    {
        log.info( "Generating \"about\" in [$aboutFile.canonicalPath], basedir is [${ basedir.canonicalPath }]" )
        write( aboutFile, allContent())
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
                    failOrWarn( failIfNotFound, "Directory [$directory.canonicalPath] is not available, consider using <failIfNotFound>" )
                    return
                }

                final split = { String s -> s ? split( s ) : null }
                final files = fileBean().files( directory, split( include ), split( exclude ), false, false, failIfNotFound )

                if ( files )
                {
                    final aboutFile = new File( outputDirectory(), fileName )
                    final prefix    = (( prefix == '/' ) ? '' : prefix )

                    writeAboutFile( aboutFile )

                    for ( f in files )
                    {
                        final aboutPath = "$f.canonicalPath/$prefix${ prefix ? '/' : '' }$fileName"

                        log.info( "Adding \"about\" to [$aboutPath]" )
                        fileBean().pack( aboutFile.parentFile, f, [ aboutFile.name ], null, false, true, true, null, null, prefix )
                        log.info( "Added  \"about\" to [$aboutPath]" )
                    }

                    fileBean().delete( aboutFile )
                }
                else
                {
                    log.warn( "No files found in [$directory.canonicalPath] and include/exclude patterns [${ include ?: '' }]/[${ exclude ?: '' }]" )
                }
            }
            else
            {
                final aboutFile = ( File ) new File( fileName ).with{ absolute ? delegate : new File( outputDirectory(), fileName )}
                writeAboutFile( aboutFile )
            }
        }
        catch ( Throwable e )
        {
            failOrWarn( failOnError, 'Failed to create "about" file', e )
        }
    }
}

