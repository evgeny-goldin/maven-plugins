package com.github.goldin.plugins.jenkins.markup

import static com.github.goldin.plugins.common.GMojoUtils.*
import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Abstract class for all {@link groovy.xml.MarkupBuilder} based markup builders.
 */
abstract class Markup
{
    /**
     * XML markup indentation.
     */
    public static final String INDENT = ' ' * 4

    /**
     * Quotation HTML entity.
     */
    public static final String QUOT = '&quot;'


    private final Writer writer
    final MarkupBuilder  builder

    @Ensures({ this.writer && this.builder })
    Markup()
    {
        this.writer          = new StringWriter ( 4 * 1024 )
        this.builder         = new MarkupBuilder( new IndentPrinter( writer, INDENT ))
        builder.doubleQuotes = true
    }


    /**
     * Builds a markup using {@link #builder}.
     */
    abstract void buildMarkup()


    /**
     * Helper method, a {@link groovy.xml.MarkupBuilderHelper#yieldUnescaped} wrapper.
     *
     * @param value value to add to the builder, unescaped.
     */
    @Requires({ value })
    final void add ( String value ) { builder.mkp.yieldUnescaped( value ) }



    final String tag( String tagName, String value ){ "<$tagName>$value</$tagName>" }


    /**
     * Retrieves a markup generated with the builder.
     * @return markup generated with the builder
     */
    @Ensures({ result })
    final String getMarkup()
    {
        buildMarkup()
        verify().notNullOrEmpty( this.writer.toString())
    }
}