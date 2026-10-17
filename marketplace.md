# AC Infinity Binding [5.0.0.0;6.0.0.0)

## AC Infinity binding

This binding connects openHAB to Wi-Fi UIS controllers through the AC Infinity cloud service. It is an initial, read-only implementation: it reports controller, device-port, and supported sensor data, but does not control devices.

## Resources

Latest available release JAR (pre-release): https://github.com/Pavion/org.openhab.binding.acinfinity/releases/download/5.0.0-Alpha/org.openhab.binding.acinfinity-5.0.0.jar

For other releases, see the [GitHub releases](https://github.com/Pavion/org.openhab.binding.acinfinity/releases).

## Readme

Add an AC Infinity account bridge using the e-mail address and password registered with the AC Infinity app. The binding discovers supported Wi-Fi UIS controllers as child Things and polls the cloud API every 10 seconds by default (minimum 5 seconds).

Controller channels report online status, temperature, humidity, and VPD. Each controller exposes eight device-port groups with the attached device name, connection status, speed/power level, and remaining time. AI controller sensor ports can report temperature, humidity, VPD, soil moisture, CO2, light, pH, water temperature, and water detection; available readings depend on the controller and attached probe.

This binding uses an undocumented AC Infinity cloud API and requires an Internet connection. The API or authentication behavior may change without notice. Bluetooth-only controllers and local/Bluetooth communication are not supported. All channels are read-only.

See the [README on GitHub](https://github.com/Pavion/org.openhab.binding.acinfinity#readme) for full configuration, channel, and compatibility details.

## Changelog

- 5.0.0-Alpha
  - First pre-release for openHAB 5.0
  - Initial read-only cloud integration with account setup, controller discovery, and telemetry channels

## Authoring and support

This binding is being developed by [@pavion](https://github.com/Pavion). It is an initial community implementation with limited support. If you encounter an issue, please reply in this thread or [create an issue on GitHub](https://github.com/Pavion/org.openhab.binding.acinfinity/issues).
