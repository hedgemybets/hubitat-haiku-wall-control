# Hubitat Elevate Haiku Wall Control Driver
This driver enables a Haiku Wall Control to be used as a motion sensor. It simply polls the wall control to get the occupancy status. The polling interval can be adjusted from 10 to 60 seconds. It's being used with 3 wall controls with polling set at 30 seconds with no issues (so far) on a hub with about 50 devices and several custom drivers and apps.

The wall controller allows TCP connections on IP port 31415. It responds to the following command:

<Living Room Wall Control;SNSROCC;STATUS;GET>

where "Living Room Wall Control" must exactly match the device Name shown in the Haiku phone app under Rooms and Devices.

There are one of two responses:

(Living Room Wall Control;SNSROCC;STATUS;UNOCCUPIED)

when the motion sensor is inactive, and

(Living Room Wall Control;SNSROCC;STATUS;OCCUPIED)

when the motion sensor has been triggered as active. Once triggered the status continues to be reported as OCCUPIED for five minutes after the motion is no longer active.

Instructions:
1. Access your Hubitat Elevate Drivers Code page and select New Driver
2. Copy the code from the haiku-wall-control.groovy file and paste it into the new driver page, then press Save
3. Go to the Devices page and select Add Virtual Device
4. Enter the Device Name exactly as it shows in the Haiku phone app. You can add a Device label, such as Living Room Occupancy Sensor.
5. Select the Haiku Wall Control Occupancy Sensor as the driver Type. It will be listed at the end under User drivers.
6. Click Save Device and the Sensor will be added.
7. On the device's page under Preferences, enter the IP address and polling Time to refresh, then click Save Preferences. 

After the device is initialized, you should see the motion state of the sensor under Current States.

Thanks to the community contributions of Zack Brown and Adam Kempenich. The driver is modelled after Adam's MagicHome WiFi driver, see https://community.hubitat.com/t/release-beta-0-7-magic-home-wifi-devices-initial-public-release/5197. Zack's Haiku Fan community thread also helped, see https://community.hubitat.com/t/haiku-fan-big-ass-fans-support/7556. I decided to use Adam's example and keep the sensors separate from Zack's fan/light driver. However, it would probably be possible to integrate this with Zack's Fan driver, since I believe that the fan's simply echo the wall control's motion sensor status.
