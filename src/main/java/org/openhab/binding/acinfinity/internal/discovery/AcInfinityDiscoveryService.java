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
package org.openhab.binding.acinfinity.internal.discovery;

import static org.openhab.binding.acinfinity.internal.AcInfinityBindingConstants.*;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.acinfinity.internal.client.AcInfinityApiException;
import org.openhab.binding.acinfinity.internal.handler.AcInfinityAccountHandler;
import org.openhab.binding.acinfinity.internal.model.AcInfinityControllerSnapshot;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class AcInfinityDiscoveryService extends AbstractDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcInfinityDiscoveryService.class);
    private static final int SEARCH_TIME_SECONDS = 15;
    private final AcInfinityAccountHandler accountHandler;

    public AcInfinityDiscoveryService(AcInfinityAccountHandler accountHandler) {
        super(SEARCH_TIME_SECONDS);
        this.accountHandler = accountHandler;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return Collections.singleton(THING_TYPE_CONTROLLER);
    }

    @Override
    protected void startScan() {
        if (accountHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            return;
        }
        try {
            for (AcInfinityControllerSnapshot controller : accountHandler.discoverControllers()) {
                ThingUID thingUID = new ThingUID(THING_TYPE_CONTROLLER, accountHandler.getThing().getUID(),
                        controller.id());
                thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(accountHandler.getThing().getUID())
                        .withLabel(controller.name()).withProperty("controllerId", controller.id()).build());
            }
        } catch (AcInfinityApiException e) {
            LOGGER.warn("Could not discover AC Infinity controllers: {}", e.getMessage());
        }
    }

    @Override
    protected void startBackgroundDiscovery() {
        startScan();
    }
}
