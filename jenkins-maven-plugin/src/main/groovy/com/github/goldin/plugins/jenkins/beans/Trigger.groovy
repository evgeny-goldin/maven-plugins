package com.github.goldin.plugins.jenkins.beans


import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.jenkins.beans.gerrit.Project


/**
 * Job trigger
 */
class Trigger
{
    static final String GERRIT_TYPE = 'gerrit'
    static final Map    TYPES       =
        [ scm             : 'hudson.triggers.SCMTrigger',
          timer           : 'hudson.triggers.TimerTrigger',
          github          : 'com.cloudbees.jenkins.GitHubPushTrigger',
          ( GERRIT_TYPE ) : 'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger' ]

    String type        = ''
    String description = ''
    String expression  = ''

    String getTriggerClass()
    {
        def    triggerClass = TYPES[ this.type ]
        assert triggerClass, "Unknown trigger <type>${ this.type }</type>. Known types are ${ TYPES.keySet() }"
               triggerClass
    }

    /**
     * Gerrit trigger properties
     */

    Project       project
    Project[]     projects
    List<Project> projects(){ generalBean().list( projects, project )}

    boolean escapeQuotes            = true
    boolean silentMode              = false
    String  verifyStarted           = ''
    String  verifySuccessful        = ''
    String  verifyFailed            = ''
    String  verifyUnstable          = ''
    String  codeReviewStarted       = ''
    String  codeReviewSuccessful    = ''
    String  codeReviewFailed        = ''
    String  codeReviewUnstable      = ''
    String  buildStartMessage       = ''
    String  buildSuccessfulMessage  = ''
    String  buildUnstableMessage    = ''
    String  buildFailureMessage     = ''
    String  unsuccessfulMessageFile = ''
    String  urlToPost               = ''
}
