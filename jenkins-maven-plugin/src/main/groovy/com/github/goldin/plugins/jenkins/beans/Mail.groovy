package com.github.goldin.plugins.jenkins.beans


/**
 * Mailing options
 */
class Mail
{
    String  recipients
    boolean sendForUnstable   = true
    boolean sendToIndividuals = true
}
