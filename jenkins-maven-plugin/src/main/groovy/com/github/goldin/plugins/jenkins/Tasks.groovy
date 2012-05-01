package com.github.goldin.plugins.jenkins

/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Free-Style projects tasks
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */

@SuppressWarnings([ 'GetterMethodCouldBeProperty' ])
abstract class Task
{
    final String space = ' ' * 4

    /**
     * Class name wrapping a task.
     */
    String   getClassName  (){ "hudson.tasks.${ this.class.simpleName }" }

    /**
     * Extra markup added by task
     */
    String   getExtraMarkup(){ '' }

    /**
     * Name of properties forming a task markup.
     */
    abstract List<String> getPropertyNames()

    /**
     * Title and command used in description table
     */
    abstract String getTitle()
    abstract String getCommand()
    final    String getCommandCut(){ cut( command ) }

    /**
     * Cuts long commands.
     */
    final cut( String s ){ (( s.size() > 80 ) ? s.substring( 0, 80 ) + ' ..' : s ) }

    /**
     * Builds task markup to be used in resulting config.
     */
    final String getMarkup()
    {
        List<String> lines = [ extraMarkup.trim() ]

        for ( property in propertyNames )
        {
            String value = this[ property ] as String
            if ( value ) { lines << "<$property>${ value.trim() }</$property>" }
        }

        final content = lines.grep().join( "\n$space" )

        className ? "<$className>\n$space$content\n</$className>" :
                    content
    }
}


@SuppressWarnings( 'StatelessClass' )
class Shell extends Task
{
    String command = ''

    @Override
    List<String> getPropertyNames(){[ 'command' ]}

    @Override
    String getTitle () { 'shell' }
}


@SuppressWarnings( 'StatelessClass' )
class BatchFile extends Task
{
    String command = ''

    @Override
    List<String> getPropertyNames(){[ 'command' ]}

    @Override
    String getTitle () { 'batch' }
}


@SuppressWarnings( 'StatelessClass' )
class Ant extends Task
{
    String antName    = ''
    String targets    = ''
    String antOpts    = ''
    String buildFile  = 'build.xml'
    String properties = ''

    @Override
    List<String> getPropertyNames(){[ ( antName ? 'antName' : '' ), 'targets', 'antOpts', 'buildFile', 'properties' ].grep() }

    @Override
    String getTitle()  { 'ant' }

    @Override
    String getCommand(){ targets ?: 'default targets' }
}


@SuppressWarnings( 'StatelessClass' )
class Maven extends Task
{
    String  targets              = Job.DEFAULT_MAVEN_GOALS
    String  mavenName            = '(Default)'
    String  jvmOptions           = ''
    String  pom                  = 'pom.xml'
    String  properties           = ''
    boolean usePrivateRepository = false

    @Override
    List<String> getPropertyNames(){[ 'targets', 'mavenName', 'jvmOptions',
                                      ( pom == 'false' ? '' : 'pom' ),
                                      'properties', 'usePrivateRepository' ].grep() }
    @Override
    String getTitle()  { 'maven' }

    @Override
    String getCommand(){ targets }
}


@SuppressWarnings([ 'StatelessClass', 'GetterMethodCouldBeProperty' ])
class Groovy extends Task
{
    boolean pre       // Whether groovy task is executed as pre or post-step
    String  command
    String  file
    String  groovyName
    String  parameters
    String  scriptParameters
    String  properties
    String  javaOpts
    String  classPath

    @Override
    String getClassName () { 'hudson.plugins.groovy.Groovy' }

    @Override
    String getExtraMarkup ( )
    {
        assert ( command || file ), 'Either <command> or <file> needs to be specified for <groovy>'
        final className  = command ? 'hudson.plugins.groovy.StringScriptSource' :
                                     'hudson.plugins.groovy.FileScriptSource'
        final commandTag = command ? 'command' :
                                     'scriptFile'

        """|<scriptSource class=\"$className\">
           |$space$space<$commandTag>${ command ?: file }</$commandTag>
           |$space</scriptSource>""".stripMargin()
    }

    @Override
    List<String> getPropertyNames (){ [ 'groovyName', 'parameters', 'scriptParameters',
                                        'properties', 'javaOpts', 'classPath' ] }

    @Override
    String getTitle()  { 'groovy' }

    @Override
    String getCommand(){ command ?: file }
}


@SuppressWarnings([ 'StatelessClass', 'GetterMethodCouldBeProperty' ])
class Xml extends Task
{
    String content

    @Override
    String getClassName (){ '' }

    @Override
    String getExtraMarkup ( ){ content }

    @Override
    List<String> getPropertyNames (){[]}

    @Override
    String getTitle()  { 'xml' }

    @Override
    String getCommand(){ 'custom task' }
}
