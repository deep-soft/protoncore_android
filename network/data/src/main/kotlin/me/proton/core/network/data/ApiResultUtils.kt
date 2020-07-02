/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.proton.core.network.data

import kotlinx.serialization.SerializationException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

internal suspend fun <Api, T> safeApiCall(
    networkManager: NetworkManager,
    api: Api,
    block: suspend (Api) -> T
): ApiResult<T> =
    try {
        ApiResult.Success(block(api))
    } catch (e: ProtonErrorException) {
        parseHttpError(e.response, e.protonData)
    } catch (e: HttpException) {
        parseHttpError(e.response()!!.raw(), null)
    } catch (e: SerializationException) {
        ApiResult.Error.Parse
    } catch (e: CertificateException) {
        ApiResult.Error.Certificate
    } catch (e: SSLHandshakeException) {
        ApiResult.Error.Certificate
    } catch (e: SocketTimeoutException) {
        ApiResult.Error.Timeout(networkManager.isConnectedToNetwork())
    } catch (e: IOException) {
        ApiResult.Error.Connection(networkManager.isConnectedToNetwork())
    }

private fun <T> parseHttpError(response: Response, protonData: ApiResult.Error.ProtonData?): ApiResult<T> {
    val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
    return if (response.code == ApiResult.HTTP_TOO_MANY_REQUESTS && retryAfter != null) {
        ApiResult.Error.TooManyRequest(retryAfter, protonData)
    } else {
        ApiResult.Error.Http(response.code, response.message, protonData)
    }
}

internal class ProtonErrorException(
    val response: Response,
    val protonData: ApiResult.Error.ProtonData
) : IOException()
