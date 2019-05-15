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

package com.microsoft.azure.cosmosdb.internal.directconnectivity;

import com.microsoft.azure.cosmosdb.BridgeInternal;
import com.microsoft.azure.cosmosdb.ConnectionPolicy;
import com.microsoft.azure.cosmosdb.DatabaseAccount;
import com.microsoft.azure.cosmosdb.internal.BaseAuthorizationTokenProvider;
import com.microsoft.azure.cosmosdb.rx.AsyncDocumentClient;
import com.microsoft.azure.cosmosdb.rx.AsyncDocumentClient.Builder;
import com.microsoft.azure.cosmosdb.rx.TestConfigurations;
import com.microsoft.azure.cosmosdb.rx.TestSuiteBase;
import com.microsoft.azure.cosmosdb.rx.internal.SpyClientUnderTestFactory;
import com.microsoft.azure.cosmosdb.rx.internal.SpyClientUnderTestFactory.ClientUnderTest;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpHeaders;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpRequest;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpResponse;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import org.apache.commons.io.IOUtils;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Single;
import rx.observers.TestSubscriber;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayServiceConfigurationReaderTest extends TestSuiteBase {

    private static final int TIMEOUT = 8000;
    private com.microsoft.azure.cosmosdb.rx.internal.http.HttpClient mockHttpClient;
    private BaseAuthorizationTokenProvider baseAuthorizationTokenProvider;
    private ConnectionPolicy connectionPolicy;
    private GatewayServiceConfigurationReader mockGatewayServiceConfigurationReader;
    private GatewayServiceConfigurationReader gatewayServiceConfigurationReader;
    private AsyncDocumentClient client;
    private String databaseAccountJson;
    private DatabaseAccount expectedDatabaseAccount;

    @Factory(dataProvider = "clientBuilders")
    public GatewayServiceConfigurationReaderTest(Builder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @BeforeClass(groups = "simple")
    public void setup() throws Exception {
        client = clientBuilder.build();
        mockHttpClient = Mockito.mock(com.microsoft.azure.cosmosdb.rx.internal.http.HttpClient.class);

        ClientUnderTest clientUnderTest = SpyClientUnderTestFactory.createClientUnderTest(this.clientBuilder);
        com.microsoft.azure.cosmosdb.rx.internal.http.HttpClient httpClient = clientUnderTest.getSpyHttpClient();
        baseAuthorizationTokenProvider = new BaseAuthorizationTokenProvider(TestConfigurations.MASTER_KEY);
        connectionPolicy = ConnectionPolicy.GetDefault();
        mockGatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                false, TestConfigurations.MASTER_KEY, connectionPolicy, baseAuthorizationTokenProvider, mockHttpClient);

        gatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                                                                                  false,
                                                                                  TestConfigurations.MASTER_KEY,
                                                                                  connectionPolicy,
                                                                                  baseAuthorizationTokenProvider,
                httpClient);
        databaseAccountJson = IOUtils
                .toString(getClass().getClassLoader().getResourceAsStream("databaseAccount.json"), "UTF-8");
        expectedDatabaseAccount = new DatabaseAccount(databaseAccountJson);
    }

    @AfterClass(groups = { "simple" }, timeOut = SHUTDOWN_TIMEOUT, alwaysRun = true)
    public void afterClass() {
        safeClose(client);
    }

    @Test(groups = "simple")
    public void mockInitializeReaderAsync() {
        HttpResponse mockResponse = getMockResponse(databaseAccountJson);

        Mockito.when(mockHttpClient.port(Mockito.anyInt())).thenReturn(mockHttpClient);
        Mockito.when(mockHttpClient.port(Mockito.anyInt()).send(Mockito.any(HttpRequest.class))).thenReturn(Mono.just(mockResponse));

        Single<DatabaseAccount> databaseAccount = mockGatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount, expectedDatabaseAccount);
    }

    @Test(groups = "simple")
    public void mockInitializeReaderAsyncWithResourceToken() throws Exception {
        mockGatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                true, "SampleResourceToken", connectionPolicy, baseAuthorizationTokenProvider, mockHttpClient);

        HttpResponse mockedResponse = getMockResponse(databaseAccountJson);

        Mockito.when(mockHttpClient.port(Mockito.anyInt())).thenReturn(mockHttpClient);
        Mockito.when(mockHttpClient.port(Mockito.anyInt()).send(Mockito.any(HttpRequest.class))).thenReturn(Mono.just(mockedResponse));

        Single<DatabaseAccount> databaseAccount = mockGatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount, expectedDatabaseAccount);
    }

    @Test(groups = "simple")
    public void initializeReaderAsync() {
        Single<DatabaseAccount> databaseAccount = gatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount);
    }

    public static void validateSuccess(Single<DatabaseAccount> observable) {
        TestSubscriber<DatabaseAccount> testSubscriber = new TestSubscriber<DatabaseAccount>();

        observable.subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent(TIMEOUT, TimeUnit.MILLISECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValueCount(1);
        assertThat(BridgeInternal.getQueryEngineConfiuration(testSubscriber.getOnNextEvents().get(0)).size() > 0).isTrue();
        assertThat(BridgeInternal.getReplicationPolicy(testSubscriber.getOnNextEvents().get(0))).isNotNull();
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0))).isNotNull();
    }

    public static void validateSuccess(Single<DatabaseAccount> observable, DatabaseAccount expectedDatabaseAccount) {
        TestSubscriber<DatabaseAccount> testSubscriber = new TestSubscriber<DatabaseAccount>();

        observable.subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent(TIMEOUT, TimeUnit.MILLISECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValueCount(1);
        assertThat(testSubscriber.getOnNextEvents().get(0).getId()).isEqualTo(expectedDatabaseAccount.getId());
        assertThat(testSubscriber.getOnNextEvents().get(0).getAddressesLink())
                .isEqualTo(expectedDatabaseAccount.getAddressesLink());
        assertThat(testSubscriber.getOnNextEvents().get(0).getWritableLocations().iterator().next().getEndpoint())
                .isEqualTo(expectedDatabaseAccount.getWritableLocations().iterator().next().getEndpoint());
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0)).getMaxReplicaSetSize())
                .isEqualTo(BridgeInternal.getSystemReplicationPolicy(expectedDatabaseAccount).getMaxReplicaSetSize());
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0)).getMaxReplicaSetSize())
                .isEqualTo(BridgeInternal.getSystemReplicationPolicy(expectedDatabaseAccount).getMaxReplicaSetSize());
        assertThat(BridgeInternal.getQueryEngineConfiuration(testSubscriber.getOnNextEvents().get(0)))
                .isEqualTo(BridgeInternal.getQueryEngineConfiuration(expectedDatabaseAccount));
    }

    private HttpResponse getMockResponse(String databaseAccountJson) {
        HttpResponse mock = Mockito.mock(HttpResponse.class);
        Mockito.doReturn(200).when(mock).statusCode();
        Mockito.doReturn(Flux.just(ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, databaseAccountJson)))
                .when(mock).body();

        Mockito.doReturn(new HttpHeaders()).when(mock).headers();
        return mock;
    }
}
