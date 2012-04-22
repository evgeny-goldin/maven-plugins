package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.junit.Test


/**
 * {@link com.github.goldin.plugins.common.GMojoUtils} tests
 */
class GMojoUtilsTest
{
    @Test
    void shouldSplit()
    {
        def check = { String input, List<String> expected -> assert expected == split( input ) }

        check( '', [] )
        check( '1', ['1'] )
        check( '1, 2',                     ['1', '2'] )
        check( ' 1 , 2 ',                  ['1', '2'] )
        check( ' 1   ,      2    ',        ['1', '2'] )
        check( '*.txt, *.pdf, *.sh|700',   ['*.txt', '*.pdf', '*.sh|700' ] )
        check( '*.txt , *.pdf , *.sh|700', ['*.txt', '*.pdf', '*.sh|700' ] )
        check( '*.txt,*.pdf,*.sh|700',     ['*.txt', '*.pdf', '*.sh|700' ] )
    }


    @Test
    void shouldAddDollar()
    {
        def check = { String input, String output, String add -> assert output == addDollar( input, add ) }

        check( 'aaa{vv}ttt{eeee}',      'aaa{vv}ttt{eeee}',       'false' )
        check( 'aaa{vv}ttt{eeee}',      'aaa{vv}ttt{eeee}',       '' )
        check( 'aaa{vv}ttt{eeee}',      'aaa{vv}ttt{eeee}',       null )
        check( 'aaa{vv}ttt{eeee}',      'aaa${vv}ttt${eeee}',     'true' )
        check( '{aaa}{vv}ttt{eeee}',    '${aaa}${vv}ttt${eeee}',  'true' )
        check( '{aaa}{vv}ttt{eeee}',    '${aaa}${vv}ttt${eeee}',  'true' )
        check( '{aaa}{vv}ttt{eeee}',    '{aaa}{vv}ttt${eeee}',    'eeee' )
        check( '{aaa}{vv}ttt{eeee}',    '{aaa}${vv}ttt${eeee}',   'eeee, vv' )
        check( '{aaa}{vv}ttt{eeee}',    '${aaa}${vv}ttt${eeee}',  'aaa , eeee, vv' )
        check( '${aaa}${vv}ttt{eeee}',  '${aaa}${vv}ttt${eeee}',  'aaa , eeee, vv' )
        check( '${aaa}${vv}ttt{eeee}',  '${aaa}${vv}ttt${eeee}',  'true' )
        check( '${aaa}${vv}ttt{eeee}',  '${aaa}${vv}ttt{eeee}',   'eee' )
        check( '{aaa}${vv}ttt{eeee}',   '${aaa}${vv}ttt{eeee}',   'aaa, vv, eee' )
        check( '{aaa}${vv}ttt{eeee}',   '${aaa}${vv}ttt{eeee}',   'aaa, eee' )
        check( '{aaa}${vv}ttt{eeee}',   '{aaa}${vv}ttt{eeee}',    'aa, vv, eee' )
        check( '{aaa}${vv}ttt{eeee}',   '{aaa}${vv}ttt{eeee}',    'aa, eee' )
        check( '$${aaa}${vv}ttt{eeee}', '$${aaa}${vv}ttt{eeee}',  'aa, eee' )
        check( '$${aaa}${vv}ttt{eeee}', '$${aaa}${vv}ttt{eeee}',  'aaa, eee' )
        check( '$${aaa}${vv}ttt{eeee}', '$${aaa}${vv}ttt${eeee}', 'aaa, eeee' )
    }
}
