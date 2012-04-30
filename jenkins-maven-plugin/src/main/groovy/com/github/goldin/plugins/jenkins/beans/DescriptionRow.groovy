package com.github.goldin.plugins.jenkins.beans

import com.github.goldin.plugins.common.GMojoUtils

/**
 * Description table row
 */
class DescriptionRow
{
    String  key
    String  value
    boolean bottom     = true
    boolean escapeHTML = true

    String getKey()   { escape( this.key   ) }
    String getValue() { escape( this.value ) }
    private String escape ( String s ){ this.escapeHTML ? GMojoUtils.escapeHtml( s ) : s }
}

