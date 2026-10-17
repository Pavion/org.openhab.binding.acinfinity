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
package org.openhab.binding.acinfinity.internal.model;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Controller telemetry. Map keys are channel IDs, including the channel group (for example {@code port1#speed}).
 */
@NonNullByDefault
public record AcInfinityControllerSnapshot(String id, String name, boolean online, @Nullable BigDecimal temperature,
        @Nullable BigDecimal humidity, @Nullable BigDecimal vpd, Map<String, BigDecimal> numbers,
        Map<String, Boolean> switches, Map<String, String> texts, Map<String, String> properties) {
}
