package com.github.goldin.plugins.jenkins.beans


/**
 * "Deploy artifacts to Maven repository" Jenkins option
 */
class Deploy
{
    String  url            = ''    /* URL of the Maven repository to deploy artifacts to */
    String  id             = ''    /* "~/.m2/settings.xml" repository ID   */
    boolean uniqueVersion  = false /* Assign unique versions to snapshots  */
    boolean evenIfUnstable = false /* Deploy even if the build is unstable */
}
