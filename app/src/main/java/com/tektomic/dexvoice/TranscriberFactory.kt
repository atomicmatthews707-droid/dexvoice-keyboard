package com.tektomic.dexvoice

object TranscriberFactory {
    fun forSettings(settings: Settings, secrets: SecretStore): Transcriber =
        if (settings.provider == Settings.PROVIDER_WISPR)
            WisprTranscriber { secrets.wisprKey }
        else
            OpenAiCompatTranscriber({ secrets.openAiKey })
}
