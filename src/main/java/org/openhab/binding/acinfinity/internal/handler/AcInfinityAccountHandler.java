/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.acinfinity.internal.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.acinfinity.internal.client.AcInfinityApiClient;
import org.openhab.binding.acinfinity.internal.client.AcInfinityApiException;
import org.openhab.binding.acinfinity.internal.client.AcInfinityAuthenticationException;
import org.openhab.binding.acinfinity.internal.config.AcInfinityAccountConfiguration;
import org.openhab.binding.acinfinity.internal.model.AcInfinityControllerSnapshot;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class AcInfinityAccountHandler extends BaseBridgeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcInfinityAccountHandler.class);
    private static final int DEFAULT_REFRESH_SECONDS = 10;
    private static final int MIN_REFRESH_SECONDS = 5;

    private final HttpClient httpClient;
    private final Map<String, AcInfinityControllerHandler> controllerHandlers = new ConcurrentHashMap<>();
    private volatile List<AcInfinityControllerSnapshot> latestControllers = List.of();
    private @Nullable AcInfinityApiClient apiClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private AcInfinityAccountConfiguration configuration = new AcInfinityAccountConfiguration();

    public AcInfinityAccountHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        configuration = getConfigAs(AcInfinityAccountConfiguration.class);
        if (configuration.email.isBlank() || configuration.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "An AC Infinity e-mail and password are required");
            return;
        }

        Integer configuredRefresh = configuration.refresh;
        int refreshSeconds = configuredRefresh == null ? DEFAULT_REFRESH_SECONDS : configuredRefresh.intValue();
        if (refreshSeconds < MIN_REFRESH_SECONDS) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "The refresh interval must be at least 5 seconds");
            return;
        }

        apiClient = new AcInfinityApiClient(httpClient);
        updateStatus(ThingStatus.UNKNOWN);
        pollingJob = scheduler.scheduleWithFixedDelay(this::refresh, 0, refreshSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        controllerHandlers.clear();
        apiClient = null;
    }

    public void registerController(AcInfinityControllerHandler handler) {
        controllerHandlers.put(handler.getControllerId(), handler);
        for (AcInfinityControllerSnapshot controller : latestControllers) {
            if (controller.id().equals(handler.getControllerId())) {
                handler.update(controller);
                break;
            }
        }
    }

    public void unregisterController(AcInfinityControllerHandler handler) {
        controllerHandlers.remove(handler.getControllerId(), handler);
    }

    public List<AcInfinityControllerSnapshot> discoverControllers() throws AcInfinityApiException {
        return fetchAndPublish();
    }

    public void refreshNow() {
        refresh();
    }

    private void refresh() {
        try {
            fetchAndPublish();
            updateStatus(ThingStatus.ONLINE);
        } catch (AcInfinityAuthenticationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
            LOGGER.warn("AC Infinity authentication failed for account bridge {}", getThing().getUID());
        } catch (AcInfinityApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            LOGGER.warn("AC Infinity cloud polling failed for account bridge {}: {}", getThing().getUID(),
                    e.getMessage());
        }
    }

    private List<AcInfinityControllerSnapshot> fetchAndPublish() throws AcInfinityApiException {
        AcInfinityApiClient client = apiClient;
        if (client == null) {
            throw new AcInfinityApiException("AC Infinity account bridge is not initialized");
        }

        List<AcInfinityControllerSnapshot> controllers = client.fetchControllers(configuration.email,
                configuration.password);
        latestControllers = List.copyOf(controllers);
        for (AcInfinityControllerSnapshot controller : controllers) {
            AcInfinityControllerHandler handler = controllerHandlers.get(controller.id());
            if (handler != null) {
                handler.update(controller);
            }
        }
        updateStatus(ThingStatus.ONLINE);
        return controllers;
    }
}
