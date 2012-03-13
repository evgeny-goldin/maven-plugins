package com.goldin.plugins.jenkins.beans


/**
 * Job trigger
 */
class Trigger
{
    static final Map TYPES = [ scm   : 'hudson.triggers.SCMTrigger',
                               timer : 'hudson.triggers.TimerTrigger' ]
    String type
    String description  // Description to be used (by normal humans) as comment
    String expression   // Crontab expression

    String getTriggerClass()
    {
        def    triggerClass = TYPES[ this.type ]
        assert triggerClass, "Unknown trigger <type>${ this.type }</type>. Known types are ${ TYPES.keySet() }"
               triggerClass
    }
}
