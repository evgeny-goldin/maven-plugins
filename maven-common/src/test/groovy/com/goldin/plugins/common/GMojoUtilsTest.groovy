package com.goldin.plugins.common

import org.junit.Test
import static com.goldin.plugins.common.GMojoUtils.*


/**
 * {@link GMojoUtils} tests
 */
class GMojoUtilsTest
{

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
