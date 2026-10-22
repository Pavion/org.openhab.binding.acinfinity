# AC Infinity Binding

This binding connects openHAB to AC Infinity Wi-Fi UIS controllers through the AC Infinity cloud service. It is an initial read-only implementation; the cloud API is unofficial and may change without notice.

## Supported Things

- **Account** bridge: AC Infinity account credentials and cloud polling.
- **Controller**: discovered Wi-Fi UIS controllers supported by the community integration: Controller 69 WiFi, Controller 69 Pro, Controller 69 Pro+, and AI+.

Bluetooth-only controllers and local/Bluetooth communication are not supported.

## Configuration

Configure the account bridge with the e-mail and password used by the AC Infinity app. The polling interval defaults to 10 seconds and cannot be set below 5 seconds.

The upstream integration documents that the mobile app truncates passwords to 25 characters for API login; this binding follows that behavior. Credentials and authentication data are not written to logs.

## Discovery

Add an AC Infinity account bridge, then run discovery for AC Infinity controllers. Controllers are discovered as child Things under the account bridge.

## Channels

| Channel | Type | Description |
| --- | --- | --- |
| `online` | Switch | Controller online status as reported by the cloud API |
| `temperature` | Number:Temperature | Controller temperature (°C) |
| `humidity` | Number:Dimensionless | Controller relative humidity (%) |
| `vpd` | Number:Pressure | Controller vapor pressure deficit (kPa) |

Channel groups `port1` to `port8` (device ports):

| Channel | Type | Description |
| --- | --- | --- |
| `name` | String | Name of the device attached to the port |
| `online` | Switch | Whether a device is connected |
| `mode` | String | Current operating mode (`OFF`, `ON`, `AUTO`, `VPD`, or `UNKNOWN (<code>)`) |
| `load-state` | Switch | Whether a load is detected; distinct from the connection status |
| `speed` | Number | Current speed/power level (0-10) |
| `remaining-time` | Number:Time | Time until the next mode change (s) |

Channel groups `sensor1` and `sensor2` (sensor ports of AI controllers; channels stay `UNDEF` when the attached probe does not provide the reading):

| Channel | Type | Description |
| --- | --- | --- |
| `temperature` | Number:Temperature | Probe temperature (°C) |
| `humidity` | Number:Dimensionless | Probe relative humidity (%) |
| `vpd` | Number:Pressure | Probe vapor pressure deficit (kPa) |
| `soil-moisture` | Number:Dimensionless | Soil moisture (%) |
| `co2` | Number:Dimensionless | CO2 concentration (ppm) |
| `light` | Number:Dimensionless | Light level (%) |
| `ph` | Number | Hydroponic pH |
| `water-temperature` | Number:Temperature | Hydroponic water temperature (°C) |
| `water-detected` | Switch | Water sensor state |

Channels are read-only. Controller properties (firmware, hardware, MAC address, controller type, time zone, port count) are set when reported by the API. The number of ports is fixed at 8 device ports and 2 sensor ports; available fields vary with controller model.
Port mode values are based on community documentation for the unofficial API: `1` = `OFF`, `2` = `ON` (manual), `3` = `AUTO`, and `8` = `VPD`. Unrecognized values are reported as `UNKNOWN (<code>)`.

## Limitations

This binding uses an undocumented cloud API and requires an Internet connection. API availability, authentication behavior, and response formats may change. Its API field mappings are informed by the community [Home Assistant AC Infinity integration](https://github.com/dalinicus/homeassistant-acinfinity), not an official AC Infinity API contract. It does not use MQTT or Home Assistant as an intermediary, and it does not support Bluetooth-only devices or write/control channels.

## Credits and Licensing

This binding is licensed under the Eclipse Public License 2.0.
It was written with the assistance of an AI coding assistant (GitHub Copilot) and reviewed and tested by the maintainer.

The AC Infinity cloud API is not officially documented.
The endpoints and field names used here were derived from the MIT-licensed [homeassistant-acinfinity](https://github.com/dalinicus/homeassistant-acinfinity) project, whose work made this binding possible.
No source code from that project is included.
AC Infinity is a trademark of its owner; this project is not affiliated with or endorsed by AC Infinity.
