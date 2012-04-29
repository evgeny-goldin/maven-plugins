package com.github.goldin.plugins.jenkins.beans


/**
 * Job trigger
 */
class Trigger
{
    static final Map TYPES = [ scm    : 'hudson.triggers.SCMTrigger',
                               timer  : 'hudson.triggers.TimerTrigger',
                               github : 'com.cloudbees.jenkins.GitHubPushTrigger' ]
    String type
    String description = ''  // Description to be used (by humans) as comment
    String expression  = ''  // Trigger expression

    String getTriggerClass()
    {
        def    triggerClass = TYPES[ this.type ]
        assert triggerClass, "Unknown trigger <type>${ this.type }</type>. Known types are ${ TYPES.keySet() }"
               triggerClass
    }
}
