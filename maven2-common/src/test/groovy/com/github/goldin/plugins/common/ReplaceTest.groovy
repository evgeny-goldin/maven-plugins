package com.github.goldin.plugins.common

import org.junit.Test
import static com.github.goldin.plugins.common.GMojoUtils.*


/**
 * {@link Replace} unit tests
 */
class ReplaceTest
{
    private static final String MAIL         = 'someone@somewhere.com'
    private static final String MAIL_PATTERN = /(\w+)@(\w+)\.(\w+)/


    ReplaceTest ()
    {
        initTestThreadLocals()
    }

    /**
     *
     * @param input
     * @param replace
     * @param expectedOutput
     * @param expectedException
     * @param expectedErrorMessage
     */
    static void replace ( String                     input,
                          Replace                    replace,
                          String                     expectedOutput,
                          Class<? extends Throwable> expectedException    = null,
                          String                     expectedErrorMessage = null )
    {
        Closure replaceCall = { replace.replace( input ) }

        if ( expectedException )
        {
            String errorMessage = new GroovyTestCase().shouldFail( expectedException, replaceCall )

            if ( expectedErrorMessage )
            {
                assert expectedErrorMessage == errorMessage, \
                    "Test should have failed with [$expectedErrorMessage] error message but it failed with [$errorMessage] instead"
            }
        }
        else
        {
            assert expectedOutput == replaceCall()
        }
    }



    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReturnEmptyStringWhenNoFromNoTo()
    {
        replace( '', new Replace(), '' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceAllInputWhenNoFrom()
    {
        replace( '',              new Replace( to: 'aaa' ), 'aaa' )
        replace( 'aaa',           new Replace( to: 'aaa' ), 'aaa' )
        replace( 'aaaaa',         new Replace( to: 'aaa' ), 'aaa' )
        replace( 'bbbbbb',        new Replace( to: 'aaa' ), 'aaa' )
        replace( 'anything else', new Replace( to: 'aaa' ), 'aaa' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenFromAndTo()
    {
        replace( '123456789',         new Replace( from: /\d/,     to: '!' ), '!' * 9 )
        replace( '123456789',         new Replace( from: /\d+/,    to: '!' ), '!' )
        replace( 'aaaaabbbb',         new Replace( from: /(a|b)/,  to: 'z' ), 'z' * 9 )
        replace( 'aaaaabbbb',         new Replace( from: /(a|b)+/, to: 'z' ), 'z' )
        replace( MAIL,                new Replace( from: /\w+/,    to: '?' ), '?@?.?' )
        replace( '.................', new Replace( from: /./,      to: '!' ), '!' * 17 )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenReference()
    {
        replace( 'aaa{bbb}ccc{ddd}',
                 new Replace( from: /(\w+)/, to: '$1' ),
                 'aaa{bbb}ccc{ddd}' )

        replace( 'aaabbbcccddd',
                 new Replace( from: /(\w+)(\w+)(\w+)/, to: '$1$2$3' ),
                 'aaabbbcccddd' )

        replace( 'aaabbbcccdd4',
                 new Replace( from: /(\w+)(\w+)(\d+)/, to: '$3$2' ),
                 '4d' )

        replace( 'aaabbbcccded',
                 new Replace( from: /(\w+)(\w+)(\w+)/, to: '$2' ),
                 'e' )

        replace( 'aaabbbcccddd',
                 new Replace( from: /(\w+)/, to: '$1$2' ),
                 'aaabbbcccddd',
                 IndexOutOfBoundsException,
                 'No group 2' )

        replace( MAIL, new Replace( from: MAIL_PATTERN, to: '$1@$2.$3' ), MAIL )
        replace( MAIL, new Replace( from: MAIL_PATTERN, to: '$3@$2.$1' ), 'com@somewhere.someone' )
        replace( MAIL, new Replace( from: MAIL_PATTERN, to: '$3@$1.$2' ), 'com@someone.somewhere' )
        replace( MAIL, new Replace( from: MAIL_PATTERN, to: '$3' * 5   ), 'com'  * 5 )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenReferenceAndQuote()
    {
        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '$1@$2.$3' ),
                 MAIL )

        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '$1@$2.$3',
                                    quoteReplacement: false ),
                 MAIL )

        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '$1@$2.$3',
                                    quoteReplacement: true ),
                 '$1@$2.$3' )

        replace( MAIL, new Replace( from            : /(\w+)@(\w+)/,
                                    to              : 'aaa$1$2$3$4$5bbb',
                                    quoteReplacement: true ),
                 'aaa$1$2$3$4$5bbb.com' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenEscape()
    {
        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\n' * 2 ),
                 '\n' * 2 )

        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\n' * 2,
                                    endOfLine: 'windows' ),
                 '\r\n' * 2 )

        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\n' * 2,
                                    endOfLine: 'linux' ),
                 '\n' * 2 )

        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\\' * 4 ),
                 '\\' * 2 )

        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\\' ),
                 '',
                 StringIndexOutOfBoundsException,
                 'String index out of range: 1' )

        replace( MAIL, new Replace( from     : MAIL_PATTERN,
                                    to       : '\\' * 3 ),
               '',
               StringIndexOutOfBoundsException,
               'String index out of range: 3' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenEscapeAndQuote()
    {
        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '\\',
                                    quoteReplacement: true ),
               '\\' )

        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '\\' * 3,
                                    quoteReplacement: true ),
               '\\' * 3 )

        replace( MAIL, new Replace( from            : MAIL_PATTERN,
                                    to              : '\\' * 4,
                                    quoteReplacement: true ),
               '\\' * 4 )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenAddDollar()
    {
        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '?' ),
                 '?{?}?{?}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '?',
                                                  addDollar: 'false'  ),
                 '?{?}?{?}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '?',
                                                  addDollar: 'true' ),
                 '?${?}?${?}' )

        replace( 'aaa${bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                   to       : '?',
                                                   addDollar: 'true' ),
                 '?${?}?${?}' )

        replace( 'aaa${bbb}ccc${ddd}', new Replace( from     : /\w+/,
                                                    to       : '?',
                                                    addDollar: 'true' ),
                 '?${?}?${?}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '!',
                                                  addDollar: 'true' ),
                 '!${!}!${!}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '!',
                                                  addDollar: 'b' ),
                 '!{!}!{!}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '??',
                                                  addDollar: '??' ),
                 '??${??}??${??}' )

        replace( 'aaa{bbb}ccc{ddd}', new Replace( from     : /\w+/,
                                                  to       : '@@',
                                                  addDollar: '??' ),
                 '@@{@@}@@{@@}' )

        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: 'bbb'         ), 'aaa${bbb}ccc{ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: 'b'           ), 'aaa{bbb}ccc{ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: ''            ), 'aaa{bbb}ccc{ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: '  '          ), 'aaa{bbb}ccc{ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: 'qqqqq'       ), 'aaa{bbb}ccc{ddd}' )
        replace( 'aaa${bbb}ccc{ddd}',  new Replace( addDollar: 'bbb'         ), 'aaa${bbb}ccc{ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: 'ddd'         ), 'aaa{bbb}ccc${ddd}' )
        replace( 'aaa{bbb}ccc${ddd}',  new Replace( addDollar: 'ddd'         ), 'aaa{bbb}ccc${ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: ' bbb , ddd ' ), 'aaa${bbb}ccc${ddd}' )
        replace( 'aaa${bbb}ccc${ddd}', new Replace( addDollar: ' bbb , ddd ' ), 'aaa${bbb}ccc${ddd}' )
        replace( 'aaa{bbb}ccc{ddd}',   new Replace( addDollar: 'bbb,ddd'     ), 'aaa${bbb}ccc${ddd}' )
        replace( 'aaa${bbb}ccc${ddd}', new Replace( addDollar: 'bbb,ddd'     ), 'aaa${bbb}ccc${ddd}' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceWhenReplaceAll()
    {
        replace( 'aaabbbcccddd', new Replace( from      : /\w/,
                                              to        : '!' ),
                 '!' * 12 )

        replace( 'aaabbbcccddd', new Replace( from      : /\w/,
                                              to        : '!',
                                              replaceAll: true ),
                 '!' * 12 )

        replace( 'aaabbbcccddd', new Replace( from      : /\w+/,
                                              to        : '!' ),
                 '!' )

        replace( 'aaabbbcccddd', new Replace( from      : /\w+/,
                                              to        : '!',
                                              replaceAll: true ),
                 '!' )

        replace( 'aaabbbcccddd', new Replace( from      : /\w+/,
                                              to        : '!',
                                              replaceAll: false ),
                 '!' )

        replace( 'a2a3b4c5c6d7', new Replace( from      : /\d/,
                                              to        : '!',
                                              replaceAll: true ),
                 'a!a!b!c!c!d!' )

        replace( 'a2a3b4c5c6d7', new Replace( from      : /\d/,
                                              to        : '!',
                                              replaceAll: false ),
                 'a!a3b4c5c6d7' )

        replace( 'a2a3b4c5c6d7', new Replace( from      : /[5-7]/,
                                              to        : '@' ),
                 'a2a3b4c@c@d@' )

        replace( 'a2a3b4c5c6d7', new Replace( from      : /[5-7]/,
                                              to        : '@',
                                              replaceAll: false ),
                 'a2a3b4c@c6d7' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldFailIfNotFound()
    {
        replace( 'aaabbbcccddd', new Replace( from : /\d/,
                                              to   : '!' ),
                 '',
                 AssertionError )

        replace( 'aaabbbcccddd', new Replace( from          : /\d/,
                                              to            : '!',
                                              failIfNotFound: true ),
                 '',
                 AssertionError )

        replace( 'aaabbbcccddd', new Replace( from          : /\d/,
                                              to            : '!',
                                              failIfNotFound: false ),
                 'aaabbbcccddd' )

        replace( 'aaabbbcccddd', new Replace( from          : /e/,
                                              to            : '!',
                                              failIfNotFound: false ),
                 'aaabbbcccddd' )
    }


    @Test
    @SuppressWarnings( 'JUnitTestMethodWithoutAssert' )
    void shouldReplaceGroovy()
    {
        replace( 'aaabb454641bcccddd', new Replace( from : /\d+/, to : 'qwe {{ 1 + 2 }} rty' ),
                 'aaabbqwe {{ 1 + 2 }} rtybcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from  : /\d+/,
                                                    to    : 'qwe {{ 1 + 2 }} rty',
                                                    groovy: false ),
                 'aaabbqwe {{ 1 + 2 }} rtybcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from  : /\d+/,
                                                    to    : 'qwe {{ 1 + 2 }} rty {{}}{{a}}{{b}}',
                                                    groovy: false ),
                 'aaabbqwe {{ 1 + 2 }} rty {{}}{{a}}{{b}}bcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from  : /\d+/,
                                                    to    : 'qwerty',
                                                    groovy: true ),
                 'aaabbqwertybcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from  : /\d+/,
                                                    to    : 'qwe {{ 1 + 2 + 3 }} rty{{}}{{"qaz"}}',
                                                    groovy: true ),
                 'aaabbqwe 6 rtynullqazbcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from      : /[a-b]+/,
                                                    to        : '{{ [ "a" : "b" ].size() }}{{ [].size() }}{{( 3 .. 1 ).collect{ it } }}',
                                                    replaceAll: false,
                                                    groovy    : true ),
                 '10[3, 2, 1]454641bcccddd' )

        replace( 'aaabb454641bcccddd', new Replace( from      : /[a-z]+/,
                                                    to        : '{{ ( 1 .. 10 ).findAll{ it > 5 }.size() }}',
                                                    replaceAll: true,
                                                    groovy    : true ),
                 '54546415' )
    }
}
