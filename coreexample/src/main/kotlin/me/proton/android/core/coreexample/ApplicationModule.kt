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

package me.proton.android.core.coreexample

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.android.core.coreexample.Constants.BASE_URL
import me.proton.android.core.coreexample.api.CoreExampleApiClient
import me.proton.core.humanverification.data.repository.HumanVerificationLocalRepositoryImpl
import me.proton.core.humanverification.data.repository.HumanVerificationRemoteRepositoryImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationLocalRepository
import me.proton.core.humanverification.domain.repository.HumanVerificationRemoteRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkManager
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.humanverification.HumanVerificationDetails
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import javax.inject.Singleton

/**
 * Application module singleton for Hilt dependencies.
 */
@Module
@InstallIn(ApplicationComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    fun provideNetworkManager(@ApplicationContext context: Context): NetworkManager =
        NetworkManager(context)

    @Provides
    @Singleton
    fun provideNetworkPrefs(@ApplicationContext context: Context) =
        NetworkPrefs(context)

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient =
        CoreExampleApiClient()

    @Provides
    @Singleton
    fun provideApiFactory(
        apiClient: ApiClient,
        networkManager: NetworkManager,
        networkPrefs: NetworkPrefs,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener
    ): ApiFactory = ApiFactory(
        BASE_URL, apiClient, CoreExampleLogger(), networkManager, networkPrefs, sessionProvider, sessionListener,
        CoroutineScope(Job() + Dispatchers.Default)
    )

    @Provides
    @Singleton
    fun provideApiProvider(apiFactory: ApiFactory): ApiProvider =
        ApiProvider(apiFactory)

    @Provides
    fun provideSessionProvider(): SessionProvider = object : SessionProvider {
        override fun getSession(sessionId: SessionId): Session? {
            TODO("AccountManagerImpl implement SessionProvider.")
        }
    }

    @Provides
    fun provideSessionListener(): SessionListener = object : SessionListener {
        override fun onSessionTokenRefreshed(session: Session) {
            TODO("AccountManagerImpl implement SessionListener")
        }

        override fun onSessionForceLogout(session: Session) {
            TODO("AccountManagerImpl implement SessionListener")
        }

        override suspend fun onHumanVerificationNeeded(
            session: Session,
            details: HumanVerificationDetails?
        ): SessionListener.HumanVerificationResult {
            TODO("AccountManagerImpl implement SessionListener")
        }
    }

    @Provides
    fun provideLocalRepository(@ApplicationContext context: Context): HumanVerificationLocalRepository =
        HumanVerificationLocalRepositoryImpl(context)

    @Provides
    fun provideRemoteRepository(apiProvider: ApiProvider): HumanVerificationRemoteRepository =
        HumanVerificationRemoteRepositoryImpl(apiProvider)
}
