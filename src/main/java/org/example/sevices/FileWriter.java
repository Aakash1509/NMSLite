package org.example.sevices;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWriter extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWriter.class);

    @Override
    public void start()
    {
        vertx.eventBus().<JsonObject>localConsumer(Constants.FILE_WRITE, message ->
        {
            vertx.executeBlocking(promise ->
            {
                try
                {
                    var metricData = message.body().getJsonObject("metrics");

                    var timestamp = message.body().getString("timestamp");

                    var context = metricData.getJsonObject("result");

                    var ip = metricData.getString("ip");

                    var filePath = Constants.BASE_DIRECTORY + "/" + String.format("%s.txt", timestamp);

                    // If file exists then append data or else write in a new file
                    if (vertx.fileSystem().existsBlocking(filePath))
                    {
                        appendToFile(filePath, ip, context);
                    }
                    else
                    {
                        writeFile(filePath, ip, context);
                    }

                    promise.complete();
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error during file operation: {}", exception.getMessage(), exception);

                    promise.fail(exception);
                }
            });
        });
    }

    private void appendToFile(String filePath, String ip, JsonObject metrics)
    {
        try
        {
            vertx.fileSystem().writeFileBlocking(filePath, Buffer.buffer(vertx.fileSystem().readFileBlocking(filePath).toString() + "---\n" + new JsonObject()
                    .put("ip", ip)
                    .put("result", metrics)
                    .encodePrettily()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error appending to file: {}", exception.getMessage(), exception);

            throw exception;
        }
    }

    private void writeFile(String filePath, String ip, JsonObject metrics)
    {
        try
        {
            vertx.fileSystem().writeFileBlocking(filePath, Buffer.buffer(new JsonObject()
                    .put("ip", ip)
                    .put("result", metrics)
                    .encodePrettily()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error writing to file: {}", exception.getMessage(), exception);

            throw exception;
        }
    }
}
