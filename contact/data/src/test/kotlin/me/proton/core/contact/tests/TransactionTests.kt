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

package me.proton.core.contact.tests

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import me.proton.core.contact.domain.entity.ContactEmailId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionTests: ContactDatabaseTests() {

    @Test
    fun `delete contact delete contact and emails`() = runBlocking {
        db.contactDao().insertOrUpdate(User0.Contact0.contactEntity)
        db.contactEmailDao().insertOrUpdate(User0.Contact0.ContactEmail0.contactEmailEntity)
        db.contactDao().deleteContact(User0.Contact0.contactId)
        assert(db.contactDao().getContact(User0.Contact0.contactId).firstOrNull() == null)
        assert(db.contactEmailDao().getAllContactsEmails(User0.Contact0.contactId).first().isEmpty())
    }

    @Test
    fun `delete all contacts from user also delete all contacts and emails from user`() = runBlocking {
        db.contactDao().insertOrUpdate(User0.Contact0.contactEntity)
        db.contactEmailDao().insertOrUpdate(User0.Contact0.ContactEmail0.contactEmailEntity)
        db.contactDao().deleteAllContacts(User0.userId)
        assert(db.contactDao().getContact(User0.Contact0.contactId).firstOrNull() == null)
        assert(db.contactEmailDao().getAllContactsEmails(User0.Contact0.contactId).first().isEmpty())
    }

    @Test
    fun `delete all contacts delete all contacts and emails`() = runBlocking {
        db.contactDao().insertOrUpdate(User0.Contact0.contactEntity)
        db.contactEmailDao().insertOrUpdate(User0.Contact0.ContactEmail0.contactEmailEntity)
        db.contactDao().deleteAllContacts()
        assert(db.contactDao().getContact(User0.Contact0.contactId).firstOrNull() == null)
        assert(db.contactEmailDao().getAllContactsEmails(User0.Contact0.contactId).first().isEmpty())
    }

    @Test
    fun `insert or update contacts emails apply correct diff`() = runBlocking {
        db.contactDao().insertOrUpdate(User0.Contact0.contactEntity)
        db.contactEmailDao().insertOrUpdate(User0.Contact0.ContactEmail0.contactEmailEntity)
        db.contactEmailLabelDao().insertOrUpdate(*User0.Contact0.ContactEmail0.emailLabelEntities)

        val updatedLabels = User0.Contact0.ContactEmail0.contactEmail.labelIds.map { it.reversed() }
        val updatedContactEmail = User0.Contact0.ContactEmail0.contactEmail.copy(
            labelIds = updatedLabels
        )
        db.insertOrUpdateContactsEmails(User0.userId, listOf(updatedContactEmail))

        assert(db.contactEmailLabelDao().getAllLabels(User0.Contact0.ContactEmail0.contactEmailId).first() == updatedLabels)
    }

    @Test
    fun `insert or update contact apply correct diff`() = runBlocking {
        val baseCards = listOf(contactCard("card-a"))
        val updatedCards = listOf(contactCard("card-b"))
        val baseEmails = listOf(User0.Contact0.contactId.contactEmail(ContactEmailId("a"), emptyList()))
        val updatedEmails = listOf(User0.Contact0.contactId.contactEmail(ContactEmailId("b"), emptyList()))
        val baseContact = User0.Contact0.contactWithCards.copy(
            contact = User0.Contact0.contactWithCards.contact.copy(contactEmails = baseEmails),
            contactCards = baseCards,
        )
        val updatedContact = User0.Contact0.contactWithCards.copy(
            contact = User0.Contact0.contactWithCards.contact.copy(contactEmails = updatedEmails),
            contactCards = updatedCards,
        )

        db.insertOrUpdateWithCards(User0.userId, baseContact)
        assert(db.getContact(User0.Contact0.contactId).first() == baseContact)
        db.insertOrUpdateWithCards(User0.userId, updatedContact)
        assert(db.getContact(User0.Contact0.contactId).first() == updatedContact)
    }
}