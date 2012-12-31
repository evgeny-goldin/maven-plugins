package com.github.goldin.plugins.jenkins.beans


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

    private String escape ( String s ){ this.escapeHTML ? s.replace( '<', '&lt;'   ).
                                                            replace( '>', '&gt;'   ).
                                                            replace( '"', '&quot;' ) :
                                                          s }
}

