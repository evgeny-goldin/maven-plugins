package com.github.goldin.plugins.jenkins

import org.gcontracts.annotations.Requires

/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Free-Style projects tasks
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */


abstract class Task
{
    final String space = ' ' * 4

    /**
     * Class name wrapping a task.
     */
    String getMarkupClassName (){ "hudson.tasks.${ this.class.simpleName }" }

    /**
     * Extra markup added by task
     */
    String   getExtraMarkup(){ '' }

    /**
     * Name of properties forming task markup
     */
    abstract List<String> getPropertyNames()

    /**
     * Title and command used in description table
     */
    abstract String getDescriptionTableTitle ()
    abstract String getDescriptionTableCommand()

    /**
     * Validates task configuration correctness.
     */
    abstract void validate()

    @Requires({ descriptionTableCommand && descriptionTableTitle })
    final    String getCommandShortened(){ descriptionTableCommand.with { size() > 80 ? substring( 0, 80 ) + ' ..' : delegate }}

    /**
     * Builds task markup to be used in resulting config.
     */
    final String getMarkup()
    {
        validate()

        List<String> markupLines = [ extraMarkup.trim() ]

        for ( propertyName in propertyNames )
        {
            assert propertyName
            markupLines << "<$propertyName>${ this[ propertyName ].toString().trim() }</$propertyName>"
        }

        final content = markupLines.grep().join( "\n$space" )

        markupClassName ? "<$markupClassName>\n$space$content\n</$markupClassName>" :
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
    String getDescriptionTableTitle  (){ 'shell' }

    @Override
    String getDescriptionTableCommand(){ command }

    @Override
    void validate (){ assert command, "Task <shell>: <command> is not specified" }
}


@SuppressWarnings( 'StatelessClass' )
class BatchFile extends Task
{
    String command = ''

    @Override
    List<String> getPropertyNames(){[ 'command' ]}

    @Override
    String getDescriptionTableTitle  () { 'batch' }

    @Override
    String getDescriptionTableCommand(){ command }

    @Override
    void validate (){ assert command, "Task <batchFile>: <command> is not specified" }
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
    String getDescriptionTableTitle  (){ 'ant' }

    @Override
    String getDescriptionTableCommand(){ targets ?: 'default targets' }

    @Override
    void validate (){ assert buildFile, "Task <ant>: <buildFile> is not specified" }
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
    String getDescriptionTableTitle  (){ 'maven' }

    @Override
    String getDescriptionTableCommand(){ targets }

    @Override
    void validate ()
    {
        assert targets, "Task <maven>: <targets> not specified"
        assert pom,     "Task <maven>: <pom> is not specified"
    }
}


@SuppressWarnings([ 'StatelessClass' ])
class Groovy extends Task
{
    boolean pre              = false       // Whether groovy task is executed as pre or post-step
    String  command          = ''
    String  file             = ''
    String  groovyName       = ''
    String  parameters       = ''
    String  scriptParameters = ''
    String  properties       = ''
    String  javaOpts         = ''
    String  classPath        = ''

    @Override
    String getMarkupClassName () { 'hudson.plugins.groovy.Groovy' }

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
    String getDescriptionTableTitle  (){ 'groovy' }

    @Override
    String getDescriptionTableCommand(){ command ?: file }

    @Override
    void validate ()
    {
        assert   ( command || file ), "Task <groovy>: <command> or <file> needs to be specified"
        assert ! ( command && file ), "Task <groovy>: both <command> and <file> can't be used"
    }
}


@SuppressWarnings([ 'StatelessClass' ])
class Gradle extends Task
{
    String  description        = ''
    String  switches           = ''
    String  tasks              = ''
    String  rootBuildScriptDir = '.'
    String  buildFile          = 'build.gradle'
    String  gradleName         = ''
    boolean useWrapper         = false

    @Override
    String getMarkupClassName (){ 'hudson.plugins.gradle.Gradle' }

    @Override
    List<String> getPropertyNames (){ [ 'description', 'switches', 'tasks', 'rootBuildScriptDir',
                                        'buildFile', 'gradleName', 'useWrapper' ] }

    @Override
    String getDescriptionTableTitle  (){ 'gradle' }

    @Override
    String getDescriptionTableCommand(){ tasks ?: 'default tasks' }

    @Override
    void validate ()
    {
        assert rootBuildScriptDir,             "Task <gradle>: <rootBuildScriptDir> is not specified"
        assert buildFile,                      "Task <gradle>: <buildFile> is not specified"
        assert ! ( gradleName && useWrapper ), "Task <gradle>: both <gradleName> and <useWrapper> can't be used"
    }
}


@SuppressWarnings([ 'StatelessClass' ])
class Xml extends Task
{
    String content = ''

    @Override
    String getMarkupClassName (){ '' }

    @Override
    String getExtraMarkup ( ){ content }

    @Override
    List<String> getPropertyNames (){[]}

    @Override
    String getDescriptionTableTitle  (){ 'xml' }

    @Override
    String getDescriptionTableCommand(){ 'custom task' }

    @Override
    void validate (){ assert content, "Task <xml>: <content> is not specified" }
}
