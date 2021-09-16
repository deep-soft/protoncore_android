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

package me.proton.core.contact.data.api

import me.proton.core.contact.data.api.response.ContactEmailResponse
import me.proton.core.contact.data.api.response.ListItemContactResponse
import me.proton.core.contact.domain.entity.Contact
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider

class ContactApiHelper(private val provider: ApiProvider) {

    suspend fun getAllContacts(userId: UserId): List<Contact> {
        val apiContacts = getAllApiContacts(userId)
        val apiContactEmails = getAllApiContactsEmails(userId)
        return apiContacts.entries.map { entry ->
            val contactEmails = (apiContactEmails[entry.key] ?: emptyList()).map { it.toContactEmail() }
            Contact(
                id = entry.key,
                name = entry.value.name,
                contactEmails = contactEmails
            )
        }
    }

    private suspend fun getAllApiContacts(userId: UserId): Map<ContactId, ListItemContactResponse> {
        return provider.get<ContactApi>(userId).invoke {
            val contacts = mutableMapOf<ContactId, ListItemContactResponse>()
            var pageIndex = 0
            val pageSize = 1000
            var shouldContinuePaging = true
            while (shouldContinuePaging) {
                val apiResult = getContacts(page = pageIndex, pageSize = pageSize)
                apiResult.contacts.forEach { contacts[ContactId(it.id)] = it }
                shouldContinuePaging = pageIndex < apiResult.total / pageSize
                pageIndex++
            }
            contacts
        }.valueOrThrow
    }

    private suspend fun getAllApiContactsEmails(userId: UserId): Map<ContactId, List<ContactEmailResponse>> {
        return provider.get<ContactApi>(userId).invoke {
            val contactEmails = mutableMapOf<ContactId, MutableList<ContactEmailResponse>>()
            var pageIndex = 0
            val pageSize = 1000
            var shouldContinuePaging = true
            while (shouldContinuePaging) {
                val apiResult = getContactEmails(page = pageIndex, pageSize = pageSize)
                apiResult.contactEmails.forEach {
                    contactEmails.getOrPut(ContactId(it.contactId), { mutableListOf() }).add(it)
                }
                shouldContinuePaging = pageIndex < apiResult.total / pageSize
                pageIndex++
            }
            contactEmails
        }.valueOrThrow
    }
}