
import java.text.SimpleDateFormat
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.Project

/**
 * http://groovy.codehaus.org/The+groovydoc+Ant+task
 */

def    groovydocDir = System.getProperty( 'groovydocDir' )
assert groovydocDir, "System property [groovydocDir] is not available"

def mavenVersion    = project.properties[ 'maven-version'    ]
def gcommonsVersion = project.properties[ 'gcommons-version' ]
def destinationDir  = new File( groovydocDir, project.version.contains( '-SNAPSHOT' ) ? '' : project.version ).canonicalPath
def d               = new Date()
def time            = new SimpleDateFormat( "HH:mm '(GMT'Z')'" ).format( d )
def date            = new SimpleDateFormat( 'MMMM dd, yyyy' ).format( d )
def basedir         = project.basedir.canonicalPath
def project         = new Project()
def path            = { String path -> path ? new Path( project, path ) : new Path( project ) }
def sourcePaths     = path( '' )
[ 'maven-assert-plugin',
  'maven-common',
  'maven-copy-plugin',
  'maven-find-plugin',
  'maven-hudson-plugin',
  'maven-mail-plugin',
  'maven-properties-plugin',
  'maven-spring-batch-plugin',
  'maven-sshexec-plugin',
  'maven-timestamp-plugin',
].each { sourcePaths.add( path( new File( "$basedir/../$it/src/main/groovy/" ).canonicalPath )) }

println "Generating GroovyDoc: [$sourcePaths] => [$destinationDir]"

def ant = new AntBuilder()
ant.taskdef(name: 'groovydoc', classname: 'org.codehaus.groovy.ant.Groovydoc' )
ant.groovydoc(
        destdir      : destinationDir,
        sourcepath   : sourcePaths,
        use          : "true",
        private      : "false",
        header       : '<a href="http://evgeny-goldin.com/wiki/Maven-plugins">evgeny-goldin.com/wiki/Maven-plugins</a>',
        footer       : '<a href="http://evgeny-goldin.com/">Evgeny Goldin</a>. GrovyDoc is generated at ' + "$time on $date." + """
                        <script type='text/javascript'>
                          var _gaq = _gaq || [];
                          _gaq.push(['_setAccount', 'UA-7925869-2']);
                          _gaq.push(['_trackPageview']);
                          (function() {
                            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
                            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
                            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
                          })();
                        </script>
                        """.stripIndent(),
    {
       link( packages:"java.,org.xml.,javax.,org.xml.",     href:"http://download.oracle.com/javase/6/docs/api"    )
       link( packages:"groovy.,org.codehaus.groovy.",       href:"http://groovy.codehaus.org/api"                  )
       link( packages:"org.apache.tools.ant.",              href:"http://evgeny-goldin.org/javadoc/ant/api"        )
       link( packages:"org.junit.,junit.framework.",        href:"http://kentbeck.github.com/junit/javadoc/latest" )
       link( packages:"org.apache.commons.net.",            href:"http://commons.apache.org/net/apidocs"           )
       link( packages:"de.schlichtherle.",                  href:"http://truezip.java.net/apidocs"                )
       link( packages:"org.codehaus.gmaven.",               href:"http://evgeny-goldin.org/javadoc/gmaven" )
       link( packages:"org.apache.maven.shared.filtering.", href:"http://maven.apache.org/shared/maven-filtering/apidocs" )
       link( packages:"org.apache.maven.artifact.",         href:"http://maven.apache.org/ref/$mavenVersion/maven-artifact/apidocs" )
       link( packages:"org.apache.maven.project.",          href:"http://maven.apache.org/ref/$mavenVersion/maven-project/apidocs" )
       link( packages:"org.apache.maven.",                  href:"http://maven.apache.org/ref/$mavenVersion/maven-core/apidocs" )
       link( packages:"com.goldin.gcommons.",               href:"http://evgeny-goldin.org/groovydoc/gcommons/$gcommonsVersion" )
    }
)

println "Generating GroovyDoc: Done"
