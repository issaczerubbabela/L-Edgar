package com.sheetsync.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {
    /** POST unsynced records to the Apps Script Web App. */
    @POST
    suspend fun syncRecords(@Url url: String, @Body request: SyncRequest): Response<SyncResponse>

    /** GET all historical records from the Apps Script Web App for initial import. */
    @GET
    suspend fun importRecords(
        @Url url: String,
        @Query("target") target: String = "transactions"
    ): Response<ImportResponse>

    /** GET dropdown configuration from Apps Script. */
    @GET
    suspend fun importDropdownOptions(
        @Url url: String,
        @Query("target") target: String = "dropdowns"
    ): Response<DropdownImportResponse>
}
