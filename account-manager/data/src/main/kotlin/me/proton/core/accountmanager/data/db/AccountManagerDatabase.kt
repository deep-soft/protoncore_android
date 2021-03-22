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

package me.proton.core.accountmanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import me.proton.core.account.data.db.AccountConverters
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.HumanVerificationDetailsEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.accountmanager.data.db.migration.MIGRATION_1_2
import me.proton.core.accountmanager.data.db.migration.MIGRATION_2_3
import me.proton.core.crypto.android.keystore.CryptoConverters
import me.proton.core.data.db.CommonConverters
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserConverters
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity

@Database(
    entities = [
        // account-data
        AccountEntity::class,
        AccountMetadataEntity::class,
        HumanVerificationDetailsEntity::class,
        SessionEntity::class,
        SessionDetailsEntity::class,
        // user-data
        UserEntity::class,
        UserKeyEntity::class,
        AddressEntity::class,
        AddressKeyEntity::class,
        // key-data
        KeySaltEntity::class,
        PublicAddressEntity::class,
        PublicAddressKeyEntity::class
    ],
    version = 3
)
@TypeConverters(
    CommonConverters::class,
    AccountConverters::class,
    UserConverters::class,
    CryptoConverters::class
)
abstract class AccountManagerDatabase :
    RoomDatabase(),
    AccountDatabase,
    UserDatabase,
    AddressDatabase,
    KeySaltDatabase,
    PublicAddressDatabase {

    override suspend fun <R> inTransaction(block: suspend () -> R): R = withTransaction(block)

    companion object {
        fun buildDatabase(context: Context): AccountManagerDatabase {
            return Room
                .databaseBuilder(context, AccountManagerDatabase::class.java, "db-account-manager")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}
