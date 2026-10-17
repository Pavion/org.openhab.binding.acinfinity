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

import static org.openhab.binding.acinfinity.internal.AcInfinityBindingConstants.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.acinfinity.internal.model.AcInfinityControllerSnapshot;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class AcInfinityControllerHandler extends BaseThingHandler {

    private @Nullable AcInfinityAccountHandler accountHandler;

    public AcInfinityControllerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        AcInfinityAccountHandler handler = accountHandler;
        if (command instanceof RefreshType && handler != null) {
            handler.refreshNow();
        }
    }

    @Override
    public void initialize() {
        Bridge bridge = getBridge();
        if (bridge == null || !(bridge.getHandler() instanceof AcInfinityAccountHandler handler)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "An AC Infinity account bridge is required");
            return;
        }
        accountHandler = handler;
        handler.registerController(this);
    }

    @Override
    public void dispose() {
        AcInfinityAccountHandler handler = accountHandler;
        if (handler != null) {
            handler.unregisterController(this);
            accountHandler = null;
        }
    }

    public String getControllerId() {
        return getThing().getUID().getId();
    }

    public void update(AcInfinityControllerSnapshot snapshot) {
        updateStatus(ThingStatus.ONLINE);
        mergeProperties(snapshot.properties());
        updateState(new ChannelUID(getThing().getUID(), CHANNEL_ONLINE),
                snapshot.online() ? OnOffType.ON : OnOffType.OFF);
        updateNumber(CHANNEL_TEMPERATURE, snapshot.temperature());
        updateNumber(CHANNEL_HUMIDITY, snapshot.humidity());
        updateNumber(CHANNEL_VPD, snapshot.vpd());

        for (Channel channel : getThing().getChannels()) {
            String id = channel.getUID().getId();
            if (snapshot.numbers().containsKey(id)) {
                updateNumber(id, snapshot.numbers().get(id));
            } else if (snapshot.switches().containsKey(id)) {
                updateState(channel.getUID(), OnOffType.from(Boolean.TRUE.equals(snapshot.switches().get(id))));
            } else if (snapshot.texts().containsKey(id)) {
                updateState(channel.getUID(), new StringType(snapshot.texts().get(id)));
            } else if (id.contains("#") && !id.startsWith("controller#")) {
                updateState(channel.getUID(), UnDefType.UNDEF);
            }
        }
    }

    private void mergeProperties(Map<String, String> values) {
        Map<String, String> properties = new HashMap<>(getThing().getProperties());
        if (!properties.entrySet().containsAll(values.entrySet())) {
            properties.putAll(values);
            updateProperties(properties);
        }
    }

    private void updateNumber(String channelId, @Nullable BigDecimal value) {
        ChannelUID uid = new ChannelUID(getThing().getUID(), channelId);
        if (value == null) {
            updateState(uid, UnDefType.UNDEF);
            return;
        }
        String name = channelId.substring(channelId.indexOf('#') + 1);
        Unit<?> unit = switch (name) {
            case "temperature", "water-temperature" -> SIUnits.CELSIUS;
            case "humidity", "soil-moisture", "light" -> Units.PERCENT;
            case "vpd" -> MetricPrefix.KILO(SIUnits.PASCAL);
            case "co2" -> Units.PARTS_PER_MILLION;
            case "remaining-time" -> Units.SECOND;
            default -> null;
        };
        updateState(uid, unit == null ? new DecimalType(value) : new QuantityType<>(value, unit));
    }
}
