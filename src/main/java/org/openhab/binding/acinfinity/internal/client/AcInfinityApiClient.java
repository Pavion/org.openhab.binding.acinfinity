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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.Fields;
import org.openhab.binding.acinfinity.internal.model.AcInfinityControllerSnapshot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

@NonNullByDefault
public class AcInfinityApiClient {

    private static final String API_HOST = "https://www.acinfinityserver.com";
    private static final String LOGIN_PATH = "/api/user/appUserLogin";
    private static final String CONTROLLERS_PATH = "/api/user/devInfoListAll";
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final int SENSOR_CONTROLLER_TEMPERATURE_F = 4;
    private static final int SENSOR_CONTROLLER_TEMPERATURE_C = 5;
    private static final int SENSOR_CONTROLLER_HUMIDITY = 6;
    private static final int SENSOR_CONTROLLER_VPD = 7;
    private static final int DEVICE_PORT_COUNT = 8;
    private static final int SENSOR_PORT_COUNT = 2;

    private final HttpClient httpClient;
    private @Nullable String userId;

    public AcInfinityApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<AcInfinityControllerSnapshot> fetchControllers(String email, String password)
            throws AcInfinityApiException {
        if (userId == null) {
            login(email, password);
        }
        String authenticatedUserId = userId;
        if (authenticatedUserId == null) {
            throw new AcInfinityAuthenticationException("AC Infinity did not return an account ID");
        }
        return parseControllerList(post(CONTROLLERS_PATH, "userId", authenticatedUserId).toString());
    }

    static List<AcInfinityControllerSnapshot> parseControllerList(String json) throws AcInfinityApiException {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            validateResponseCode(response, false);
            JsonElement data = response.get("data");
            if (data == null || !data.isJsonArray()) {
                throw new AcInfinityApiException("AC Infinity returned an invalid controller list");
            }

            List<AcInfinityControllerSnapshot> controllers = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new AcInfinityApiException("AC Infinity returned an invalid controller entry");
                }
                controllers.add(parseController(element.getAsJsonObject()));
            }
            return controllers;
        } catch (JsonParseException | IllegalStateException e) {
            throw new AcInfinityApiException("AC Infinity returned malformed controller data", e);
        }
    }

    private void login(String email, String password) throws AcInfinityApiException {
        String apiPassword = password.length() > 25 ? password.substring(0, 25) : password;
        JsonObject response = post(LOGIN_PATH, "appEmail", email, "appPasswordl", apiPassword);
        JsonElement data = response.get("data");
        if (data == null || !data.isJsonObject()) {
            throw new AcInfinityAuthenticationException("AC Infinity login response did not contain account data");
        }
        JsonElement id = data.getAsJsonObject().get("appId");
        if (id == null || id.isJsonNull()) {
            throw new AcInfinityAuthenticationException("AC Infinity login response did not contain an account ID");
        }
        userId = id.getAsString();
    }

    private JsonObject post(String path, String... parameters) throws AcInfinityApiException {
        try {
            Request request = httpClient.newRequest(URI.create(API_HOST + path));
            request.method(HttpMethod.POST);
            request.header("User-Agent", "okhttp/4.12.0");
            if (userId != null) {
                request.header("token", userId);
            }
            Fields form = new Fields();
            for (int i = 0; i < parameters.length; i += 2) {
                form.put(parameters[i], parameters[i + 1]);
            }
            request.content(new FormContentProvider(form));
            ContentResponse response = request.timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).send();
            if (response.getStatus() != 200) {
                throw new AcInfinityApiException("AC Infinity request failed with HTTP status " + response.getStatus());
            }

            JsonObject json = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
            validateResponseCode(json, path.equals(LOGIN_PATH));
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcInfinityApiException("AC Infinity request was interrupted", e);
        } catch (ExecutionException | TimeoutException | JsonParseException | IllegalStateException e) {
            throw new AcInfinityApiException("Could not complete AC Infinity API request", e);
        }
    }

    private static void validateResponseCode(JsonObject response, boolean login) throws AcInfinityApiException {
        JsonElement code = response.get("code");
        if (code != null && code.isJsonPrimitive()) {
            try {
                if (code.getAsInt() == 200) {
                    return;
                }
            } catch (NumberFormatException | IllegalStateException e) {
                // Report an invalid response below with the endpoint-appropriate error.
            }
        }
        if (login) {
            throw new AcInfinityAuthenticationException("AC Infinity rejected the account credentials");
        }
        throw new AcInfinityApiException("AC Infinity returned an unsuccessful API response");
    }

    private static AcInfinityControllerSnapshot parseController(JsonObject controller) throws AcInfinityApiException {
        String id = requiredString(controller, "devId");
        String name = requiredString(controller, "devName");
        boolean online = readOnline(controller.get("online"));

        // Readings are reported in "deviceInfo" as integers with two implied decimals (e.g. 2460 = 24.60).
        JsonObject info = controller.has("deviceInfo") && controller.get("deviceInfo").isJsonObject()
                ? controller.getAsJsonObject("deviceInfo")
                : new JsonObject();
        BigDecimal temperature = scaled(readNumber(controller, info, "temperature"));
        BigDecimal humidity = scaled(readNumber(controller, info, "humidity"));
        BigDecimal vpd = scaled(readNumber(controller, info, "vpdnums"));

        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        Map<String, Boolean> switches = new LinkedHashMap<>();
        Map<String, String> texts = new LinkedHashMap<>();

        // AI controllers expose the controller readings in a sensor array only.
        JsonElement sensors = info.get("sensors");
        if (sensors != null && sensors.isJsonArray()) {
            boolean zeroBasedPorts = false;
            for (JsonElement element : sensors.getAsJsonArray()) {
                if (element.isJsonObject() && sensorType(element.getAsJsonObject()) < SENSOR_CONTROLLER_TEMPERATURE_F
                        && accessPort(element.getAsJsonObject()) == 0) {
                    zeroBasedPorts = true;
                }
            }
            for (JsonElement element : sensors.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject sensor = element.getAsJsonObject();
                BigDecimal value = sensorValue(sensor);
                if (value == null) {
                    continue;
                }
                int type = sensorType(sensor);
                switch (type) {
                    case SENSOR_CONTROLLER_TEMPERATURE_F ->
                        temperature = temperature != null ? temperature : fahrenheitToCelsius(value);
                    case SENSOR_CONTROLLER_TEMPERATURE_C -> temperature = temperature != null ? temperature : value;
                    case SENSOR_CONTROLLER_HUMIDITY -> humidity = humidity != null ? humidity : value;
                    case SENSOR_CONTROLLER_VPD -> vpd = vpd != null ? vpd : value;
                    default -> {
                        int port = accessPort(sensor) + (zeroBasedPorts ? 1 : 0);
                        if (port >= 1 && port <= SENSOR_PORT_COUNT) {
                            putProbeValue(numbers, switches, "sensor" + port + "#", type, value);
                        }
                    }
                }
            }
        }

        JsonElement ports = info.get("ports");
        if (ports != null && ports.isJsonArray()) {
            for (JsonElement element : ports.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject port = element.getAsJsonObject();
                BigDecimal portNumber = readNumberOrNull(port, "port");
                if (portNumber == null || portNumber.intValue() < 1 || portNumber.intValue() > DEVICE_PORT_COUNT) {
                    continue;
                }
                String prefix = "port" + portNumber.intValue() + "#";
                String portName = optionalString(port, "portName");
                if (portName != null) {
                    texts.put(prefix + "name", portName);
                }
                BigDecimal mode = readNumberOrNull(port, "curMode");
                if (mode != null) {
                    texts.put(prefix + "mode", portMode(mode));
                }
                try {
                    if (port.has("online") && !port.get("online").isJsonNull()) {
                        switches.put(prefix + "online", readOnline(port.get("online")));
                    }
                } catch (AcInfinityApiException e) {
                    // optional enrichment: leave the channel undefined
                }
                try {
                    if (port.has("loadState") && !port.get("loadState").isJsonNull()) {
                        switches.put(prefix + "load-state", readOnline(port.get("loadState")));
                    }
                } catch (AcInfinityApiException e) {
                    // optional enrichment: leave the channel undefined
                }
                putNumber(numbers, prefix + "speed", readNumberOrNull(port, "speak"));
                putNumber(numbers, prefix + "remaining-time", readNumberOrNull(port, "remainTime"));
            }
        }

        Map<String, String> properties = new LinkedHashMap<>();
        putProperty(properties, "firmwareVersion", controller, info, "firmwareVersion");
        putProperty(properties, "hardwareVersion", controller, info, "hardwareVersion");
        putProperty(properties, "macAddress", controller, info, "devMacAddr");
        putProperty(properties, "controllerType", controller, info, "devType");
        putProperty(properties, "timeZone", controller, info, "zoneId");
        putProperty(properties, "portCount", controller, info, "devPortCount");

        return new AcInfinityControllerSnapshot(id, name, online, temperature, humidity, vpd, numbers, switches, texts,
                properties);
    }

    private static String portMode(BigDecimal mode) {
        String code = mode.stripTrailingZeros().toPlainString();
        return switch (code) {
            case "1" -> "OFF";
            case "2" -> "ON";
            case "3" -> "AUTO";
            case "8" -> "VPD";
            default -> "UNKNOWN (" + code + ")";
        };
    }

    private static void putProbeValue(Map<String, BigDecimal> numbers, Map<String, Boolean> switches, String prefix,
            int type, BigDecimal value) {
        switch (type) {
            case 0 -> numbers.put(prefix + "temperature", fahrenheitToCelsius(value));
            case 1 -> numbers.put(prefix + "temperature", value);
            case 2 -> numbers.put(prefix + "humidity", value);
            case 3 -> numbers.put(prefix + "vpd", value);
            case 10 -> numbers.put(prefix + "soil-moisture", value);
            case 11 -> numbers.put(prefix + "co2", value);
            case 12 -> numbers.put(prefix + "light", value);
            case 13 -> numbers.put(prefix + "ph", value);
            case 18 -> numbers.put(prefix + "water-temperature", fahrenheitToCelsius(value));
            case 19 -> numbers.put(prefix + "water-temperature", value);
            case 20 -> switches.put(prefix + "water-detected", value.signum() > 0);
            default -> {
            }
        }
    }

    private static BigDecimal fahrenheitToCelsius(BigDecimal value) {
        return value.subtract(BigDecimal.valueOf(32)).multiply(BigDecimal.valueOf(5)).divide(BigDecimal.valueOf(9), 1,
                RoundingMode.HALF_UP);
    }

    private static int accessPort(JsonObject sensor) {
        BigDecimal port = readNumberOrNull(sensor, "accessPort");
        return port == null ? -1 : port.intValue();
    }

    private static @Nullable BigDecimal readNumberOrNull(JsonObject object, String property) {
        try {
            return readNumber(object, property);
        } catch (AcInfinityApiException e) {
            return null;
        }
    }

    private static void putNumber(Map<String, BigDecimal> numbers, String key, @Nullable BigDecimal value) {
        if (value != null) {
            numbers.put(key, value);
        }
    }

    private static @Nullable String optionalString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            return null;
        }
        return value.getAsString();
    }

    private static void putProperty(Map<String, String> properties, String key, JsonObject top, JsonObject info,
            String property) {
        String value = optionalString(top, property);
        if (value == null) {
            value = optionalString(info, property);
        }
        if (value != null) {
            properties.put(key, value);
        }
    }

    private static @Nullable BigDecimal scaled(@Nullable BigDecimal value) {
        return value == null ? null : value.movePointLeft(2);
    }

    private static int sensorType(JsonObject sensor) {
        JsonElement type = sensor.get("sensorType");
        try {
            return type == null || type.isJsonNull() ? -1 : type.getAsInt();
        } catch (NumberFormatException | IllegalStateException | UnsupportedOperationException e) {
            return -1;
        }
    }

    private static @Nullable BigDecimal sensorValue(JsonObject sensor) {
        try {
            JsonElement data = sensor.get("sensorData");
            if (data == null || data.isJsonNull()) {
                return null;
            }
            JsonElement precision = sensor.get("sensorPrecision");
            int digits = precision == null || precision.isJsonNull() ? 0 : Math.max(precision.getAsInt() - 1, 0);
            return data.getAsBigDecimal().movePointLeft(digits);
        } catch (NumberFormatException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static @Nullable BigDecimal readNumber(JsonObject top, JsonObject info, String property)
            throws AcInfinityApiException {
        BigDecimal value = readNumber(top, property);
        return value != null ? value : readNumber(info, property);
    }

    private static String requiredString(JsonObject object, String property) throws AcInfinityApiException {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            throw new AcInfinityApiException("AC Infinity controller data is missing " + property);
        }
        return value.getAsString();
    }

    private static boolean readOnline(@Nullable JsonElement value) throws AcInfinityApiException {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            throw new AcInfinityApiException("AC Infinity controller data is missing online status");
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        String status = value.getAsString();
        if ("1".equals(status) || "true".equalsIgnoreCase(status)) {
            return true;
        }
        if ("0".equals(status) || "false".equalsIgnoreCase(status)) {
            return false;
        }
        throw new AcInfinityApiException("AC Infinity returned an invalid controller online status");
    }

    private static @Nullable BigDecimal readNumber(JsonObject object, String property) throws AcInfinityApiException {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull() || (value.isJsonPrimitive() && value.getAsString().isBlank())) {
            return null;
        }
        if (!value.isJsonPrimitive()) {
            throw new AcInfinityApiException("AC Infinity returned an invalid numeric value for " + property);
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | IllegalStateException e) {
            throw new AcInfinityApiException("AC Infinity returned an invalid numeric value for " + property, e);
        }
    }
}
