package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * <replace> data container
 */
@SuppressWarnings( 'StatelessClass' )
class Replace
{
    /**
     * Regex pattern to match a Groovy {{ .. }} expression in replacement <code>String</code>.
     *
     * @see #replaceAll
     */
    private static final Pattern GROOVY_EXPRESSION_PATTERN = ~/\{\{(.*?)\}\}/

    String  from                          // "From" Regexp pattern
    String  to                            // "To" value to replace the data matched with {@link #from}
    String  endOfLine                     // Whether "end of line" should be that of Windows ("\r\n") or Linux ("\n")
    String  addDollar        = 'false'    // Whether all {..} expressions will be converted to ${..}
                                          // Accepts values 'true', 'false' and comma-separated list of variable names
    boolean quoteReplacement = false      // Whether {@link Matcher#quoteReplacement(String)} should be used on <code>to</code>
    boolean replaceAll       = true       // Whether {@link Matcher#replaceAll(String)} should be used or {@link Matcher#replaceFirst(String)}
    boolean failIfNotFound   = true       // Whether an exception should be thrown if pattern wasn't matched
    boolean groovy           = false      // Whether {{ .. }} expressions in replacement should be Groovy eval()-ed


    /**
     * Makes replacements in the <code>String</code> specified using {@link #from}
     * {@link Pattern} and {@link #to} replacement.
     *
     * @param data    <code>String</code> to replace
     * @param filePath file the data was read from
     *
     * @return {@link #to} if {@link #from} is <code>null</code>,
     *         <b>{@code pattern.matcher( s ).replaceAll( replacement )}</b> otherwise where
     *         <code>pattern</code> is {@code Pattern.compile( getFrom())} and
     *         <code>replacement</code> is {@code quoteReplacement() ? Matcher.quoteReplacement( getTo()) : getTo()}
     */
    @SuppressWarnings([ 'ConfusingTernary', 'TernaryCouldBeElvis', 'UnnecessarySemicolon' ])
    String replace( String data, File file = null )
    {
        //noinspection GroovyConditionalCanBeElvis
        String result = ( to                     ? to   :  // If we have <to>, then replacement starts with it
                        ( 'false' != addDollar ) ? data :  // If we have no <to> but <addDollar>, replacement starts with data specified
                                                     '' )  // Otherwise, replacement starts with empty String
        if ( groovy )
        {
            /**
             * Finding each {{ .. }} expression in result and replacing it with Groovy evaluation
             */
            for ( Matcher matcher = ( result =~ GROOVY_EXPRESSION_PATTERN );
                    matcher.find();
                          matcher = ( result =~ GROOVY_EXPRESSION_PATTERN ))
            {
                String expression = matcher.group( 1 )
                String value      = eval( expression, String )
                result            = result.substring( 0, matcher.start()) + value + result.substring( matcher.end())
            }
        }

        if ( from )
        {
            String  replacement = ( quoteReplacement ? Matcher.quoteReplacement( result ) : result )
            Matcher matcher     = ( data =~ /$from/ )

            assert ( matcher.find() || ( ! failIfNotFound )) : \
                   "Failed to match [$from] pattern in [${ file ?: data }]"

            result = replaceAll ? matcher.replaceAll  ( replacement ) :
                                  matcher.replaceFirst( replacement )
        }

        result = addDollar( result, addDollar )

        ( endOfLine ) ? result.replaceAll( /\r?\n/, (( 'windows' == endOfLine ) ? '\r\n' : '\n' )) :
                        result
    }
}
