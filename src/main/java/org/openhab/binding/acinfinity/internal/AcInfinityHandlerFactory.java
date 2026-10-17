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
package org.openhab.binding.acinfinity.internal;

import static org.openhab.binding.acinfinity.internal.AcInfinityBindingConstants.*;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.acinfinity.internal.discovery.AcInfinityDiscoveryService;
import org.openhab.binding.acinfinity.internal.handler.AcInfinityAccountHandler;
import org.openhab.binding.acinfinity.internal.handler.AcInfinityControllerHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.acinfinity")
public class AcInfinityHandlerFactory extends BaseThingHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcInfinityHandlerFactory.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(BRIDGE_TYPE_ACCOUNT, THING_TYPE_CONTROLLER);

    private final Map<Thing, ServiceRegistration<?>> discoveryRegistrations = new ConcurrentHashMap<>();
    private @Nullable HttpClient httpClient;

    @Reference
    protected void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        httpClient = httpClientFactory.getCommonHttpClient();
    }

    protected void unsetHttpClientFactory(HttpClientFactory httpClientFactory) {
        httpClient = null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (BRIDGE_TYPE_ACCOUNT.equals(thing.getThingTypeUID()) && thing instanceof Bridge bridge) {
            HttpClient client = httpClient;
            if (client == null) {
                LOGGER.error("Cannot create AC Infinity account handler: HTTP client service is unavailable");
                return null;
            }
            AcInfinityAccountHandler handler = new AcInfinityAccountHandler(bridge, client);
            AcInfinityDiscoveryService discoveryService = new AcInfinityDiscoveryService(handler);
            ServiceRegistration<?> registration = bundleContext.registerService(DiscoveryService.class.getName(),
                    discoveryService, new Hashtable<>());
            discoveryRegistrations.put(thing, registration);
            return handler;
        }
        if (THING_TYPE_CONTROLLER.equals(thing.getThingTypeUID())) {
            return new AcInfinityControllerHandler(thing);
        }
        return null;
    }

    @Override
    protected void removeHandler(ThingHandler handler) {
        ServiceRegistration<?> registration = discoveryRegistrations.remove(handler.getThing());
        if (registration != null) {
            registration.unregister();
        }
        super.removeHandler(handler);
    }
}
