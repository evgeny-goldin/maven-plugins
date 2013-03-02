
import java.text.SimpleDateFormat
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.Project
import com.github.goldin.gcommons.GCommons

/**
 * http://docs.codehaus.org/display/GROOVY/The+groovydoc+Ant+task
 */

String groovydocDir = System.getProperty( 'groovydocDir' )
assert groovydocDir, 'System property [groovydocDir] is not defined, add -DgroovydocDir=<directory>.'

def basedir         = project.basedir.canonicalPath
def mavenVersion    = project.properties[ 'maven-version'    ]
def gcommonsVersion = project.properties[ 'gcommons-version' ]
def version         = project.version

File   destinationDir = new File( groovydocDir, version.contains( '-SNAPSHOT' ) ? '' : version ).canonicalFile
assert destinationDir.with{ directory || mkdirs() }

def d               = new Date()
def time            = new SimpleDateFormat( "HH:mm '(GMT'Z')'", new Locale( 'en' )).format( d )
def date            = new SimpleDateFormat( 'MMMM dd, yyyy',    new Locale( 'en' )).format( d )
def project         = new Project()
def path            = { String path -> path ? new Path( project, path ) : new Path( project ) }
def sourcePaths     = path( '' )

[ 'about-maven-plugin',
  'assert-maven-plugin',
  'copy-maven-plugin',
  'duplicates-finder-plugin',
  'find-maven-plugin',
  'ivy-maven-plugin',
  'jenkins-maven-plugin',
  'mail-maven-plugin',
  'maven-common',
  'properties-maven-plugin',
  'spring-batch-maven-plugin',
  'sshexec-maven-plugin',
  'timestamp-maven-plugin',
].each { sourcePaths.add( path( GCommons.verify().directory( new File( "$basedir/../$it/src/main/groovy/" )).canonicalPath )) }


def ant = new AntBuilder()
ant.taskdef( name: 'groovydoc', classname: 'org.codehaus.groovy.ant.Groovydoc' )
ant.groovydoc(
        destdir      : destinationDir,
        sourcepath   : sourcePaths,
        doctitle     : "maven-plugins $version API",
        windowtitle  : "maven-plugins $version API",
        use          : 'true',
        private      : 'false',
        header       : '<a href="http://evgeny-goldin.com/wiki/Maven-plugins">evgeny-goldin.com/wiki/Maven-plugins</a>',
        footer       : """
                       <a href="http://evgeny-goldin.com/">Evgeny Goldin</a>. GrovyDoc generated at $time on $date.
                       <script type="text/javascript">
                          var _gaq = _gaq || [];
                          _gaq.push(['_setAccount', 'UA-7925869-2']);
                          _gaq.push(['_setDomainName', 'none']);
                          _gaq.push(['_setAllowLinker', true]);
                          _gaq.push(['_trackPageview']);
                          (function() {
                            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
                            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
                            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
                          })();
                       </script>""".stripIndent())
    {
       link( packages :'org.apache.maven.artifact.',          href : "http://maven.apache.org/ref/$mavenVersion/maven-artifact/apidocs" )
       link( packages :'org.apache.maven.',                   href : "http://maven.apache.org/ref/$mavenVersion/maven-core/apidocs/" )
       link( packages :'java.,org.xml.,javax.,org.xml.',      href : 'http://docs.oracle.com/javase/7/docs/api/'    )
       link( packages :'groovy.,org.codehaus.groovy.',        href : 'http://groovy.codehaus.org/api'                  )
       link( packages :'org.springframework.batch.',          href : 'http://static.springsource.org/spring-batch/apidocs/'          )
       link( packages :'org.springframework.',                href : 'http://static.springsource.org/spring/docs/3.2.x/javadoc-api/' )
       link( packages :'org.apache.tools.ant.',               href : 'http://evgeny-goldin.org/javadoc/ant/api'        )
       link( packages :'org.junit.,junit.framework.',         href : 'http://kentbeck.github.com/junit/javadoc/latest' )
       link( packages :'org.apache.commons.net.',             href : 'http://commons.apache.org/net/apidocs'           )
       link( packages :'de.schlichtherle.',                   href : 'http://truezip.java.net/apidocs'                 )
       link( packages :'org.codehaus.gmaven.',                href : 'http://evgeny-goldin.org/javadoc/gmaven' )
       link( packages :'org.apache.maven.shared.filtering.',  href : 'http://maven.apache.org/shared/maven-filtering/apidocs' )
       link( packages :'org.apache.maven.plugin.dependency.', href : 'http://maven.apache.org/plugins/maven-dependency-plugin/apidocs' )
       link( packages :'com.github.goldin.gcommons.',         href : "http://evgeny-goldin.org/groovydoc/gcommons/$gcommonsVersion" )
    }
