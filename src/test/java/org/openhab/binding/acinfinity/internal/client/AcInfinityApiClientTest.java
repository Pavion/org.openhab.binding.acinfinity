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
package org.openhab.binding.acinfinity.internal.client;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.acinfinity.internal.model.AcInfinityControllerSnapshot;

@NonNullByDefault
class AcInfinityApiClientTest {

    private static void assertDecimal(String expected, @Nullable BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(Objects.requireNonNull(actual)));
    }

    @Test
    void parsesControllerTelemetry() throws AcInfinityApiException {
        String response = """
                {
                  "code": 200,
                  "data": [{
                    "devId": 12345,
                    "devName": "Tent Controller",
                    "online": 1,
                    "deviceInfo": {
                      "temperature": 2460,
                      "humidity": 5520,
                      "vpdnums": 131
                    }
                  }]
                }
                """;

        List<AcInfinityControllerSnapshot> controllers = AcInfinityApiClient.parseControllerList(response);

        assertEquals(1, controllers.size());
        AcInfinityControllerSnapshot controller = controllers.get(0);
        assertEquals("12345", controller.id());
        assertEquals("Tent Controller", controller.name());
        assertTrue(controller.online());
        assertDecimal("24.6", controller.temperature());
        assertDecimal("55.2", controller.humidity());
        assertDecimal("1.31", controller.vpd());
    }

    @Test
    void parsesAiControllerSensorArray() throws AcInfinityApiException {
        String response = """
                {"code":200,"data":[{"devId":"1","devName":"AI","online":1,"deviceInfo":{"sensors":[
                  {"sensorType":4,"sensorPrecision":2,"sensorData":776},
                  {"sensorType":6,"sensorPrecision":2,"sensorData":552},
                  {"sensorType":7,"sensorPrecision":3,"sensorData":131}]}}]}
                """;

        AcInfinityControllerSnapshot controller = AcInfinityApiClient.parseControllerList(response).get(0);

        assertDecimal("25.3", controller.temperature());
        assertDecimal("55.2", controller.humidity());
        assertDecimal("1.31", controller.vpd());
    }

    @Test
    void parsesPortsSensorsAndProperties() throws AcInfinityApiException {
        String response = """
                {"code":200,"data":[{"devId":"1","devName":"AI","online":1,"deviceInfo":{
                  "firmwareVersion":"3.2.1","devMacAddr":"AA:BB",
                  "ports":[{"port":1,"portName":"Fan","online":1,"curMode":3,"loadState":1,"speak":5,"remainTime":90},
                           {"port":2,"curMode":1,"loadState":0},
                           {"port":3,"curMode":2,"loadState":1},
                           {"port":4,"curMode":8,"loadState":0},
                           {"port":5,"curMode":42},
                           {"port":9,"portName":"ignored","curMode":1,"loadState":1}],
                  "sensors":[
                  {"accessPort":1,"sensorType":1,"sensorPrecision":2,"sensorData":251},
                  {"accessPort":1,"sensorType":2,"sensorPrecision":2,"sensorData":601},
                  {"accessPort":2,"sensorType":20,"sensorPrecision":1,"sensorData":1}]}}]}
                """;

        AcInfinityControllerSnapshot controller = AcInfinityApiClient.parseControllerList(response).get(0);

        assertEquals("Fan", controller.texts().get("port1#name"));
        assertEquals(Boolean.TRUE, controller.switches().get("port1#online"));
        assertEquals("AUTO", controller.texts().get("port1#mode"));
        assertEquals(Boolean.TRUE, controller.switches().get("port1#load-state"));
        assertEquals("OFF", controller.texts().get("port2#mode"));
        assertEquals(Boolean.FALSE, controller.switches().get("port2#load-state"));
        assertEquals("ON", controller.texts().get("port3#mode"));
        assertEquals("VPD", controller.texts().get("port4#mode"));
        assertEquals("UNKNOWN (42)", controller.texts().get("port5#mode"));
        assertDecimal("5", controller.numbers().get("port1#speed"));
        assertDecimal("90", controller.numbers().get("port1#remaining-time"));
        assertFalse(controller.texts().containsKey("port9#name"));
        assertFalse(controller.texts().containsKey("port9#mode"));
        assertFalse(controller.switches().containsKey("port9#load-state"));
        assertDecimal("25.1", controller.numbers().get("sensor1#temperature"));
        assertDecimal("60.1", controller.numbers().get("sensor1#humidity"));
        assertEquals(Boolean.TRUE, controller.switches().get("sensor2#water-detected"));
        assertEquals("3.2.1", controller.properties().get("firmwareVersion"));
        assertEquals("AA:BB", controller.properties().get("macAddress"));
    }

    @Test
    void acceptsOptionalTelemetryAsAbsent() throws AcInfinityApiException {
        String response = """
                {"code":200,"data":[{"devId":"abc","devName":"Controller","online":false}]}
                """;

        AcInfinityControllerSnapshot controller = AcInfinityApiClient.parseControllerList(response).get(0);

        assertFalse(controller.online());
        assertNull(controller.temperature());
        assertNull(controller.humidity());
        assertNull(controller.vpd());
    }

    @Test
    void rejectsUnsuccessfulApiResponse() {
        AcInfinityApiException exception = assertThrows(AcInfinityApiException.class,
                () -> AcInfinityApiClient.parseControllerList("{\"code\":500,\"data\":[]}"));
        assertEquals("AC Infinity returned an unsuccessful API response", exception.getMessage());
    }

    @Test
    void rejectsMalformedControllerEntries() {
        assertThrows(AcInfinityApiException.class,
                () -> AcInfinityApiClient.parseControllerList("{\"code\":200,\"data\":[{\"devId\":1}]}"));
    }
}
