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
import com.microsoft.azure.cosmosdb.DocumentClientException;
import com.microsoft.azure.cosmosdb.Error;
import com.microsoft.azure.cosmosdb.internal.HttpConstants;
import com.microsoft.azure.cosmosdb.rx.internal.RMResources;
import com.microsoft.azure.cosmosdb.rx.internal.Strings;
import com.microsoft.azure.cosmosdb.rx.internal.http.HttpHeaders;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GoneException extends DocumentClientException {

    public GoneException(String msg) {
        this(msg, null);
    }
    public GoneException() {
        this(RMResources.Gone, (String) null);
    }

    public GoneException(Error error, long lsn, String partitionKeyRangeId, Map<String, String> responseHeaders) {
        super(HttpConstants.StatusCodes.GONE, error, responseHeaders);
        BridgeInternal.setLSN(this, lsn);
        BridgeInternal.setPartitionKeyRangeId(this, partitionKeyRangeId);
    }

    public GoneException(String message, String requestUri) {
        this(message, (Exception) null, new HashMap<>(), requestUri);
    }

    public GoneException(String message,
                         Exception innerException,
                         URL requestUrl,
                         String localIpAddress) {
        this(message(localIpAddress, message), innerException, (HttpHeaders) null, requestUrl);
    }

    public GoneException(Exception innerException) {
        this(RMResources.Gone, innerException, new HashMap<>(), (String) null);
    }

    public GoneException(String message, HttpHeaders headers, URL requestUrl) {
        super(message, null, HttpUtils.asMap(headers), HttpConstants.StatusCodes.GONE, requestUrl != null ? requestUrl.toString() : null);
    }

    public GoneException(String message, HttpHeaders headers, String requestUri) {
        super(message, null, HttpUtils.asMap(headers), HttpConstants.StatusCodes.GONE, requestUri);
    }

    public GoneException(String message,
                         Exception innerException,
                         HttpHeaders headers,
                         URL requestUrl) {
        super(message, innerException, HttpUtils.asMap(headers), HttpConstants.StatusCodes.GONE, requestUrl != null ? requestUrl.toString() : null);
    }

    public GoneException(String message,
                         Exception innerException,
                         Map<String, String> headers,
                         String requestUri) {
        super(message, innerException, headers, HttpConstants.StatusCodes.GONE, requestUri);
    }

    public GoneException(Error error, Map<String, String> headers) {
        super(HttpConstants.StatusCodes.GONE, error, headers);
    }

    private static String message(String localIP, String baseMessage) {
        if (!Strings.isNullOrEmpty(localIP)) {
            return String.format(
                RMResources.ExceptionMessageAddIpAddress,
                baseMessage,
                localIP);
        }

        return baseMessage;
    }
}
