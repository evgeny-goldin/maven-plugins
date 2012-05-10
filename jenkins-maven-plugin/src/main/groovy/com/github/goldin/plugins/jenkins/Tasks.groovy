package com.github.goldin.plugins.jenkins

import org.gcontracts.annotations.Requires
import groovy.xml.MarkupBuilder

/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Free-Style projects tasks
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */


abstract class Task
{
    /**
     * Raw XML content that can be set for the task.
     */
    String content = ''

    /**
     * Class name wrapping a task.
     */
    String getMarkupClassName (){ "hudson.tasks.${ this.class.simpleName }" }

    /**
     * Builds task's markup.
     */
    abstract void buildMarkup( MarkupBuilder builder )

    /**
     * Title and command used in description table
     */
    abstract String getDescriptionTableTitle ()
    abstract String getDescriptionTableCommand()

    /**
     * Validates task configuration correctness.
     */
    abstract void validate()

    /**
     * Used by the template - retrieves a shortened version of tasks's "command".
     */
    @Requires({ descriptionTableCommand && descriptionTableTitle })
    final String getCommandShortened(){ descriptionTableCommand.with { size() > 80 ? substring( 0, 80 ) + ' ..' : delegate }}

    /**
     * Adds property values to the builder specified.
     */
    @Requires({ builder && propertyNames })
    final void addProperties( MarkupBuilder builder, Collection<String> propertyNames )
    {
        for ( propertyName in propertyNames )
        {
            assert propertyName
            builder."${ propertyName }"( this[ propertyName ] )
        }
    }

    /**
     * Builds task's markup.
     */
    final String getMarkup()
    {
        validate()

        if ( content ) { return content }

        final writer         = new StringWriter()
        final builder        = new MarkupBuilder( new IndentPrinter( writer, ' ' * 4 ))
        builder.doubleQuotes = true
        builder."${ markupClassName }" { buildMarkup( builder ) }
        writer.toString()
    }
}


@SuppressWarnings( 'StatelessClass' )
class Shell extends Task
{
    String command = ''

    @Override
    void buildMarkup ( MarkupBuilder builder )
    {
        addProperties( builder, [ 'command' ])
    }

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
    void buildMarkup ( MarkupBuilder builder )
    {
        addProperties( builder, [ 'command' ])
    }

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
    void buildMarkup ( MarkupBuilder builder )
    {
        addProperties( builder, [ ( antName ? 'antName' : '' ), 'targets', 'antOpts', 'buildFile', 'properties' ].grep())
    }

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
    void buildMarkup ( MarkupBuilder builder )
    {
        addProperties( builder, [ 'targets', 'mavenName', 'jvmOptions',
                                  ( pom == 'false' ? '' : 'pom' ),
                                  'properties', 'usePrivateRepository' ].grep())
    }

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
    void buildMarkup ( MarkupBuilder builder )
    {
        final className = 'hudson.plugins.groovy.' + ( command ? 'StringScriptSource' : 'FileScriptSource' )

        builder.scriptSource( class: className ) {
            "${ command ? 'command' : 'scriptFile' }"( command ?: file )
        }

        addProperties( builder, [ 'groovyName', 'parameters', 'scriptParameters',
                                  'properties', 'javaOpts', 'classPath' ])
    }

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
    String  tasks              = 'build'
    String  rootBuildScriptDir = '.'
    String  buildFile          = 'build.gradle'
    String  gradleName         = ''
    boolean useWrapper         = false

    @Override
    String getMarkupClassName (){ 'hudson.plugins.gradle.Gradle' }

    @Override
    void buildMarkup ( MarkupBuilder builder )
    {
        addProperties( builder, [ 'description', 'switches', 'tasks', 'rootBuildScriptDir',
                                  'buildFile', 'gradleName', 'useWrapper' ])
    }

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
    @Override
    void buildMarkup ( MarkupBuilder builder ){}

    @Override
    String getDescriptionTableTitle  (){ 'xml' }

    @Override
    String getDescriptionTableCommand(){ 'custom task' }

    @Override
    void validate (){ assert content, "Task <xml>: <content> is not specified" }
}
