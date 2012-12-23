package com.github.goldin.plugins.jenkins.markup

import static com.github.goldin.plugins.common.GMojoUtils.*
import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import com.github.goldin.plugins.jenkins.NewLineIndentPrinter


/**
 * Abstract class for all {@link groovy.xml.MarkupBuilder} based markup builders.
 */
abstract class Markup
{
    /**
     * Quotation HTML entity.
     */
    public static final String QUOT = '&quot;'


    private final Writer writer
    MarkupBuilder        builder


    Markup()
    {
        /**
         * When markup instance is created using default constructor it means no new {@link MarkupBuilder} is created
         * so it needs to be set explicitly later.
         */
    }


    @Requires({ indent && newLine })
    @Ensures({ this.writer && this.builder })
    Markup( String indent, String newLine )
    {
        this.writer          = new StringWriter ( 4 * 1024 )
        this.builder         = new MarkupBuilder( new NewLineIndentPrinter( writer, indent, newLine ))
        builder.doubleQuotes = true
    }


    /**
     * Adds a markup to the {@link #builder}.
     */
    // @Requires({ builder })
    abstract void addMarkup ()


    /**
     * Helper method, a {@link groovy.xml.MarkupBuilderHelper#yieldUnescaped} wrapper - adds a value specified
     * to the {@link #builder}, unescaped, if it evaluates to Groovy {@code true}.
     *
     * @param value value to add to the {@link #builder}, unescaped.
     */
    @Requires({ value })
    final void add ( String value ) { if ( value ) { builder.mkp.yieldUnescaped( value ) }}


    /**
     * Adds tag and a value to the {@link #builder} if value evaluates to Groovy {@code true}.
     * @param tagName name of the tag to add
     * @param value   tag's value to add
     */
    @Requires({ tagName })
    final void add ( String tagName, Object value ) { if ( value ) { builder."$tagName"( value ) }}


    /**
     * Helper methods
     */
    String code   ( String   expression )           { tag( 'code',   QUOT + expression + QUOT )}
    String strong ( String   expression )           { tag( 'strong', expression )}
    String tag    ( String   tagName, String value ){ "<$tagName>$value</$tagName>" }


    /**
     * Retrieves a markup generated with the builder.
     * @return markup generated with the builder
     */
    @Requires({ builder })
    @Ensures({ result })
    final String getMarkup()
    {
        addMarkup()
        verifyBean().notNullOrEmpty( this.writer.toString())
    }
}