package tools.mo3ta.salo.data.remote

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient
