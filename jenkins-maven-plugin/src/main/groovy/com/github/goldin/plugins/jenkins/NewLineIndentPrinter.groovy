package com.github.goldin.plugins.jenkins

import org.gcontracts.annotations.Requires


/**
 */
class NewLineIndentPrinter extends IndentPrinter
{
    private final String newLine

    @Requires({ writer && indent && newLine })
    NewLineIndentPrinter ( Writer writer, String indent, String newLine )
    {
        super( writer, indent )
        this.newLine = newLine
    }


    @Override
    void println(){ super.print( newLine ) }
}
