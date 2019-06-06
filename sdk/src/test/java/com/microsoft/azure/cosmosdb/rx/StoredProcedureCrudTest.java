/*
 * The MIT License (MIT)
 * Copyright (c) 2018 Microsoft Corporation
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.azure.cosmosdb.rx;

import java.util.UUID;

import com.microsoft.azure.cosmos.CosmosClientBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import com.microsoft.azure.cosmos.CosmosClient;
import com.microsoft.azure.cosmos.CosmosContainer;
import com.microsoft.azure.cosmos.CosmosResponse;
import com.microsoft.azure.cosmos.CosmosResponseValidator;
import com.microsoft.azure.cosmos.CosmosStoredProcedure;
import com.microsoft.azure.cosmos.CosmosStoredProcedureRequestOptions;
import com.microsoft.azure.cosmos.CosmosStoredProcedureResponse;
import com.microsoft.azure.cosmos.CosmosStoredProcedureSettings;

import reactor.core.publisher.Mono;

public class StoredProcedureCrudTest extends TestSuiteBase {

    private CosmosContainer createdCollection;

    private CosmosClient client;

    @Factory(dataProvider = "clientBuildersWithDirect")
    public StoredProcedureCrudTest(CosmosClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @Test(groups = { "simple" }, timeOut = TIMEOUT)
    public void createStoredProcedure() throws Exception {

        // create a stored procedure
        CosmosStoredProcedureSettings storedProcedureDef = new CosmosStoredProcedureSettings();
        storedProcedureDef.setId(UUID.randomUUID().toString());
        storedProcedureDef.setBody("function() {var x = 10;}");

        Mono<CosmosStoredProcedureResponse> createObservable = createdCollection.createStoredProcedure(storedProcedureDef, new CosmosStoredProcedureRequestOptions());

        // validate stored procedure creation
        CosmosResponseValidator<CosmosStoredProcedureResponse> validator = new CosmosResponseValidator.Builder<CosmosStoredProcedureResponse>()
                .withId(storedProcedureDef.getId())
                .withStoredProcedureBody("function() {var x = 10;}")
                .notNullEtag()
                .build();
        validateSuccess(createObservable, validator);
    }

    @Test(groups = { "simple" }, timeOut = TIMEOUT)
    public void readStoredProcedure() throws Exception {
        // create a stored procedure
        CosmosStoredProcedureSettings storedProcedureDef = new CosmosStoredProcedureSettings();
        storedProcedureDef.setId(UUID.randomUUID().toString());
        storedProcedureDef.setBody("function() {var x = 10;}");
        CosmosStoredProcedure storedProcedure = createdCollection.createStoredProcedure(storedProcedureDef, new CosmosStoredProcedureRequestOptions())
                .block().getStoredProcedure();

        // read stored procedure
        waitIfNeededForReplicasToCatchUp(clientBuilder);
        Mono<CosmosStoredProcedureResponse> readObservable = storedProcedure.read(null);

        CosmosResponseValidator<CosmosStoredProcedureResponse> validator = new CosmosResponseValidator.Builder<CosmosStoredProcedureResponse>()
                .withId(storedProcedureDef.getId())
                .withStoredProcedureBody("function() {var x = 10;}")
                .notNullEtag()
                .build();
        validateSuccess(readObservable, validator);
    }

    @Test(groups = { "simple" }, timeOut = TIMEOUT)
    public void deleteStoredProcedure() throws Exception {
        // create a stored procedure
        CosmosStoredProcedureSettings storedProcedureDef = new CosmosStoredProcedureSettings();
        storedProcedureDef.setId(UUID.randomUUID().toString());
        storedProcedureDef.setBody("function() {var x = 10;}");
        CosmosStoredProcedure storedProcedure = createdCollection.createStoredProcedure(storedProcedureDef, new CosmosStoredProcedureRequestOptions())
                .block().getStoredProcedure();

        // delete
        Mono<CosmosResponse> deleteObservable = storedProcedure.delete(new CosmosStoredProcedureRequestOptions());

        // validate
        CosmosResponseValidator<CosmosResponse> validator = new CosmosResponseValidator.Builder<CosmosResponse>()
                .nullResource()
                .build();
        validateSuccess(deleteObservable, validator);

        // attempt to read stored procedure which was deleted
        waitIfNeededForReplicasToCatchUp(clientBuilder);

        Mono<CosmosStoredProcedureResponse> readObservable = storedProcedure.read(null);
        FailureValidator notFoundValidator = new FailureValidator.Builder().resourceNotFound().build();
        validateFailure(readObservable, notFoundValidator);
    }

    @BeforeClass(groups = { "simple" }, timeOut = SETUP_TIMEOUT)
    public void beforeClass() {
        client = clientBuilder.build();
        createdCollection = getSharedMultiPartitionCollection(client);
    }

    @AfterClass(groups = { "simple" }, timeOut = SHUTDOWN_TIMEOUT, alwaysRun = true)
    public void afterClass() {
        safeClose(client);
    }
}
