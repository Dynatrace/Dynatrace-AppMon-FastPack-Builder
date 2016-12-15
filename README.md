# FastPack-Builder
GUI Tool to automatically create Dynatrace Fastpacks

<h4>About</h4>
This tool automatically packages and creates a fastpack based on a source directory containing the "raw" files.


<h4>Usage</h4>
Open the jar file by right clicking and opening with Java(TM) Platform Binary or the following command from the command line.

    java -jar FastPack_Builder.jar

This will open the GUI tool.

Use the GUI to select your "raw files" folder then process the dashboard.

Your fastpack will be generated and placed into the original "raw files" folder.

<h5>Web Dashboard Support</h5>
Web dashboards are stored on the server as a JSON and XML file pair.

1. Grab both files off the server ([DT_INSTALL_DIR]/server/conf/web/

2. Create a subfolder within the "raw files" folder called **webdashboards**

3. Drop the JSON and XML file(s) into this webdashboards subfolder

<h4>Current Limitations</h4>
Accepts the following files & formats:

- Dashboard files (must end with .dashboard.xml)
- System Profile Files (must end with .profile.xml)
- User Plugins (must end with .jar)
- License key files (must end with .key)
- Sensor Packs (must be the directory containing the plugin.xml file for the sensor pack. Hint: Extract the .dtcs file).

Does NOT currently support arbitrary files.
For more info, see [issues](https://github.com/Dynatrace-Adam-Gardner/FastPack-Builder/issues) for all bugs / enhancement requests.

<h4>Support</h4>
This tool is written, developed and maintened by Adam Gardner.
All support requests should be directed to adam.gardner@dynatrace.com or by creating an issue on Github.
