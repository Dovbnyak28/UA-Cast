package com.uacastplayer.dlna

/**
 * Whether a DLNA session can survive the phone's network moving under it.
 *
 * A DLNA cast is not a link to a service - it is an address. The renderer was handed
 * `http://<this phone's IPv4>:<port>/...` and fetches from it directly, so the session lasts
 * exactly as long as that address keeps reaching this phone. The moment Wi-Fi drops for mobile
 * data, or the router restarts and hands out a different lease, or the phone joins another
 * network, the URL the TV is holding points at nothing.
 *
 * **The renderer cannot tell anyone about that, and nothing here was listening.** Unlike
 * Chromecast, which has a framework that reports its own session ending, DLNA has no channel back:
 * `AvTransport` is a request/response API the phone calls, and there are no callbacks. So the only
 * way out of "connected" used to be the user pressing Stop - and after a network change the app
 * went on showing a TV it was casting to for as long as the screen stayed open, while the TV showed
 * a dead stream.
 *
 * Kept as a pure function of the two addresses because that is the whole rule, and because the
 * alternative - asserting on it - means a real renderer, two real networks and a router to restart.
 */
object DlnaNetworkChangePolicy {

    /**
     * Whether a session started while this phone was reachable at [sessionHost] is still servable
     * now that it is reachable at [currentHost].
     *
     * @param sessionHost the address baked into the URL the renderer is fetching from. Null means
     *   no session has an address yet, which nothing should be torn down over.
     * @param currentHost this phone's IPv4 on the network it can reach a renderer over, or null
     *   when there is none at all - mobile data with Wi-Fi off, or airplane mode. That is not a
     *   change of address, it is the loss of one, and the session cannot be served either way.
     */
    fun sessionSurvives(sessionHost: String?, currentHost: String?): Boolean = when {
        sessionHost == null -> true
        currentHost == null -> false
        else -> sessionHost == currentHost
    }
}
