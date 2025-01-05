package org.example.util;

import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.example.Main.*;

public class Helper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Helper.class);

    public static boolean validIp(String ip)
    {
        var regex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";

        var pattern = Pattern.compile(regex);

        var matcher = pattern.matcher(ip);

        return matcher.matches();
    }

    public static boolean validPort(int port)
    {
        return port <= 1 || port >= 65535;
    }

    public static boolean isNotUnique(Map<Long, JsonObject> map, String comparator, String column)
    {
        var unique = false;

        for(var entry : map.entrySet())
        {
            if(comparator.equals(entry.getValue().getString(column)))
            {
                unique = true;

                break;
            }
        }
        return unique;
    }

    public static void checkConnection(JsonObject deviceInfo)
    {
        try
        {
            // Setting event type to discover
            deviceInfo.put(Constants.EVENT_TYPE,Constants.DISCOVER);

            // Spawning a process
            var process = new ProcessBuilder(Constants.PLUGIN_PATH, deviceInfo.encode())
                    .redirectErrorStream(true).start();

            // Wait for the process to complete within 60 seconds
            var status = process.waitFor(60, TimeUnit.SECONDS); //(boolean)

            if (!status || process.exitValue() != 0)
            {
                process.destroy();

                LOGGER.warn("Connection check timed out");

                deviceInfo.put("credential_profile",null);

                deviceInfo.put("hostname",null);

                deviceInfo.put("status", "Down");

                return;
                // Terminate the process if it times out
            }

            // Output from the Go executable
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            var output = reader.readLine();

            LOGGER.info("Output from Go executable: {}", output);

            // Parse the output and update the deviceInfo JSON object

            var result = new JsonObject(output);

            deviceInfo.put("credential_profile", result.getLong("credential.profile.id"));

            deviceInfo.put("hostname", result.getString("hostname") != null ? result.getString("hostname").trim() : null);

            deviceInfo.put("status", result.getString("status") != null ? result.getString("status").trim() : null);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error during connection making: {}", exception.getMessage());
        }
    }

    public static boolean isProvisioned(String deviceIp)
    {
        for(var device : objects.values())
        {
            if(deviceIp.equals(device.getString("ip")))
            {
                return true;
            }
        }
        return false;
    }

    public static void insertInMap(Map<Long, JsonObject> map,Long key, JsonObject data)
    {
        map.put(key,data);
    }

    public static boolean validateField(String fieldValue)
    {
        return fieldValue == null || fieldValue.isEmpty();
    }

    public static String validateDiscovery(JsonObject requestBody)
    {
        if (requestBody == null)
        {
            return "Request body cannot be null";
        }

        var name = requestBody.getString("discovery.name");

        var ip = requestBody.getString("discovery.ip");

        var port = requestBody.getInteger("discovery.port");

        var deviceType = requestBody.getString("device.type");

        var credentialProfiles = requestBody.getJsonArray("discovery.credential.profiles");

        if (validateField(name) || validateField(ip) || validateField(String.valueOf(port)) ||
                validateField(deviceType) || validateField(String.valueOf(credentialProfiles)))
        {
            return "Please enter required fields";
        }

        if (!validIp(ip))
        {
            return "Invalid IP address provided";
        }

        if (validPort(port))
        {
            return "Invalid port provided";
        }

        if (isNotUnique(discoveries, name, "name"))
        {
            return "Discovery name should be unique";
        }

        return "";
    }

    public static String validateCredential(JsonObject requestBody)
    {
        if (requestBody == null)
        {
            return "Request body cannot be null";
        }

        var name = requestBody.getString("credential.profile.name");

        var protocol = requestBody.getString("credential.profile.protocol");

        if (validateField(name) || validateField(protocol))
        {
            return "Please enter both credential profile name and protocol";
        }

        if ("SSH".equals(protocol))
        {
            var userName = requestBody.getString("user.name");

            var userPassword = requestBody.getString("user.password");

            if (validateField(userName) || validateField(userPassword))
            {
                return "For SSH protocol, both user.name and user.password are required";
            }
        }
        else if ("SNMP".equals(protocol))
        {
            var community = requestBody.getString("community");

            var version = requestBody.getString("version");

            if (validateField(community) || validateField(version))
            {
                return "For SNMP protocol, both community and version are required";
            }
        }
        else
        {
            return "Unsupported protocol: " + protocol;
        }

        if (isNotUnique(credentials, name, "profile_name"))
        {
            return "Credential profile name should be unique";
        }

        return "";
    }
}

