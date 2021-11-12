/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.core.auth.domain.usecase.signup

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.auth.domain.entity.Modulus
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.domain.usecase.PerformLogin
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.srp.Auth
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiException
import me.proton.core.user.domain.entity.CreateUserType
import me.proton.core.user.domain.repository.UserRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PerformCreateExternalEmailUserTest {
    // region mocks
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val srpCrypto = mockk<SrpCrypto>(relaxed = true)
    private val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true)
    private val performLogin = mockk<PerformLogin>()

    // endregion

    // region test data
    private val testPassword = "test-password"
    private val testEncryptedPassword = "encrypted-$testPassword"
    private val testEmail = "test-email"
    private val testModulus = Modulus(modulusId = "test-id", modulus = "test-modulus")
    private val testAuth = Auth(
        version = 0,
        modulusId = testModulus.modulusId,
        salt = "test-salt",
        verifier = "test-verifier"
    )

    // endregion

    private lateinit var useCase: PerformCreateExternalEmailUser

    @Before
    fun beforeEveryTest() {
        // GIVEN
        useCase = PerformCreateExternalEmailUser(
            authRepository,
            userRepository,
            srpCrypto,
            keyStoreCrypto,
            performLogin
        )
        every {
            srpCrypto.calculatePasswordVerifier(testEmail, any(), any(), any())
        } returns testAuth
        every { keyStoreCrypto.decrypt(any<String>()) } returns testPassword
        every { keyStoreCrypto.encrypt(any<String>()) } returns testEncryptedPassword

        coEvery { authRepository.randomModulus() } returns testModulus

        coEvery {
            userRepository.createExternalEmailUser(any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
    }

    @Test
    fun `create external user success`() = runBlockingTest {
        coEvery { userRepository.isUsernameAvailable(eq(testEmail)) } returns true

        useCase.invoke(
            testEmail,
            keyStoreCrypto.encrypt(testPassword),
            referrer = null
        )

        coVerify(exactly = 1) { authRepository.randomModulus() }
        verify(exactly = 1) {
            srpCrypto.calculatePasswordVerifier(
                username = testEmail,
                password = any(),
                modulusId = testModulus.modulusId,
                modulus = testModulus.modulus
            )
        }
        val emailSlot = slot<String>()
        val passwordSlot = slot<EncryptedString>()
        val typeSlot = slot<CreateUserType>()
        coVerify(exactly = 1) {
            userRepository.createExternalEmailUser(
                capture(emailSlot),
                capture(passwordSlot),
                any(),
                capture(typeSlot),
                any()
            )
        }
        assertEquals(testEmail, emailSlot.captured)
        assertEquals("encrypted-$testPassword", passwordSlot.captured)
        assertEquals(CreateUserType.Normal, typeSlot.captured)
    }

    @Test
    fun `create external user empty email`() = runBlockingTest {
        val throwable = assertFailsWith<IllegalArgumentException> {
            useCase.invoke(
                "   ",
                keyStoreCrypto.encrypt(testPassword),
                referrer = null
            )
        }
        assertNotNull(throwable)
        assertEquals(
            "Email must not be empty.",
            throwable.message
        )
    }

    @Test
    fun `user already exists but can log in`() = runBlockingTest {
        val testUserId = UserId("user-id")
        coEvery { userRepository.isUsernameAvailable(testEmail) } returns false
        coEvery { performLogin.invoke(testEmail, testEncryptedPassword) } returns mockk {
            every { userId } returns testUserId
        }

        val createdUserId = useCase.invoke(
            testEmail,
            keyStoreCrypto.encrypt(testPassword),
            referrer = null
        )
        assertEquals(testUserId, createdUserId)
    }

    @Test
    fun `user already exists and cannot log in`() = runBlockingTest {
        val apiException = mockk<ApiException>()
        coEvery { userRepository.isUsernameAvailable(testEmail) } returns false
        coEvery { performLogin.invoke(testEmail, testEncryptedPassword) } throws apiException

        val result = assertFailsWith(ApiException::class) {
            useCase.invoke(
                testEmail,
                keyStoreCrypto.encrypt(testPassword),
                referrer = null
            )
        }
        assertSame(apiException, result)
    }
}
