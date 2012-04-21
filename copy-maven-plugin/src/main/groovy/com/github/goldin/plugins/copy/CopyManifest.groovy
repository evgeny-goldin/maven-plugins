package com.github.goldin.plugins.copy

import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

/**
 * Manifest content to be added to archives packed
 */
@SuppressWarnings( 'StatelessClass' )
class CopyManifest
{
    final String              defaultLocation = 'META-INF/MANIFEST.MF'
    String                    location        = defaultLocation
    final Map<String, String> entries         = [:]


    /**
     * Copies all new keys and location from the manifest specified if it contains any entries.
     * Location is copied only if the current location is identical to the default one.
     *
     * @param m manifest to copy keys and location from
     * @return this instance
     */
    @Requires({ m })
    @Ensures({ result == this })
    CopyManifest add( CopyManifest m )
    {
        if ( m.entries )
        {
            location = ( defaultLocation == location ) ? m.location : location

            m.entries.each {
                String key, String value ->
                if ( ! entries.containsKey( key )) { entries[ key ] = value }
            }
        }

        this
    }
}
