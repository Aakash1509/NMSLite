package org.example.sevices;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWriter extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(FileWriter.class);

    @Override
    public void start() {
        vertx.eventBus().<JsonObject>consumer(Constants.FILE_WRITE, message ->
        {
            vertx.executeBlocking(promise ->
            {
                try
                {
                    var data = message.body();

                    var metrics = data.getJsonObject("metrics").getJsonObject("result");

                    var timestamp = data.getString("timestamp");

                    var ip = data.getJsonObject("metrics").getString("ip");

                    var filePath = Constants.BASE_DIRECTORY + "/" + String.format("%s.txt", timestamp);

                    // Perform blocking file operations
                    if (vertx.fileSystem().existsBlocking(filePath))
                    {
                        appendToFile(filePath, ip, metrics);
                    }
                    else
                    {
                        writeFile(filePath, ip, metrics);
                    }

                    promise.complete();
                }
                catch (Exception exception)
                {
                    logger.error("Error during file operation: {}", exception.getMessage(), exception);

                    promise.fail(exception);
                }
            }, result ->
            {
                if (result.succeeded())
                {
                    logger.info("File operation completed successfully");
                }
                else
                {
                    logger.error("File operation failed: {}", result.cause().getMessage());
                }
            });
        });
    }

    private void appendToFile(String filePath, String ip, JsonObject metrics)
    {
        try
        {
            var existingData = vertx.fileSystem().readFileBlocking(filePath).toString();

            var separator = "\n---\n";

            var newData = new JsonObject()
                    .put("ip", ip)
                    .put("result", metrics)
                    .encodePrettily();

            vertx.fileSystem().writeFileBlocking(filePath, Buffer.buffer(existingData + separator + newData));

            logger.info("Data appended successfully to: {}", filePath);
        }
        catch (Exception exception)
        {
            logger.error("Error appending to file: {}", exception.getMessage(), exception);

            throw exception;
        }
    }

    private void writeFile(String filePath, String ip, JsonObject metrics)
    {
        try
        {
            var newData = new JsonObject()
                    .put("ip", ip)
                    .put("result", metrics)
                    .encodePrettily();

            vertx.fileSystem().writeFileBlocking(filePath, Buffer.buffer(newData));

            logger.info("File written successfully: {}", filePath);
        }
        catch (Exception exception)
        {
            logger.error("Error writing to file: {}", exception.getMessage(), exception);

            throw exception;
        }
    }
}
