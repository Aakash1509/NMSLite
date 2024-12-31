package org.example.database;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.example.Constants;
import org.example.Main;

import java.util.ArrayList;
import java.util.List;

public class QueryUtility
{
    private static QueryUtility instance;

    private QueryUtility()
    {

    }

    public static QueryUtility getInstance()
    {
        if(instance==null)
        {
            instance = new QueryUtility();
        }
        return instance;
    }

    private static final PgPool client;

    static
    {
        // Database connection options
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(Constants.DB_PORT)
                .setHost(Constants.DB_HOST)
                .setDatabase(Constants.DB_DATABASE)
                .setUser(Constants.DB_USER)
                .setPassword(Constants.DB_PASSWORD);

        // Pool options
        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(Constants.POOL_SIZE); // Maximum connections in the pool

        // Create client
        client = PgPool.pool(Main.vertx, connectOptions, poolOptions);
    }

    public Future<Long> insert(String tableName, JsonObject data)
    {
        var promise = Promise.<Long>promise();

        Main.vertx.executeBlocking(insert->
        {
            try
            {
                var columns = new StringBuilder();

                var placeholders = new StringBuilder();

                var values = new ArrayList<>();

                data.forEach(entry ->
                {
                    columns.append(entry.getKey()).append(", ");

                    placeholders.append("$").append(values.size() + 1).append(", ");

                    values.add(entry.getValue());
                });

                columns.setLength(columns.length() - 2);

                placeholders.setLength(placeholders.length() - 2);

                client.preparedQuery("INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ") RETURNING *") //* means every column
                        .execute(Tuple.from(values), execute -> {
                            if (execute.succeeded())
                            {
                                var rows = execute.result();

                                if (rows.size() > 0)
                                {
                                    var id = rows.iterator().next().getLong(0);

                                    insert.complete(id);
                                }
                                else
                                {
                                    insert.fail("No rows returned");
                                }
                            }
                            else
                            {
                                insert.fail(execute.cause().getMessage());
                            }
                        });
            }
            catch (Exception exception)
            {
                insert.fail(exception);
            }
        },false,result->
        {
            if (result.succeeded())
            {
                promise.complete((Long) result.result());
            }
            else
            {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    public Future<Void> delete(String tableName, String column, Long id)
    {
        var promise = Promise.<Void>promise();

        Main.vertx.executeBlocking(delete ->
        {
            try
            {
                String query = "DELETE FROM " + tableName + " WHERE " + column + " = $1";

                client.preparedQuery(query).execute(Tuple.of(id), execute ->
                {
                    if (execute.succeeded())
                    {
                        if (execute.result().rowCount() > 0)
                        {
                            delete.complete();
                        }
                        else
                        {
                            delete.fail("Information not found");
                        }
                    }
                    else
                    {
                        delete.fail(execute.cause());
                    }
                });
            }
            catch (Exception e)
            {
                delete.fail(e);
            }
        },false, result ->
        {
            if (result.succeeded())
            {
                promise.complete();
            }
            else
            {
                promise.fail(result.cause());
            }
        });
        return promise.future();
    }


    public Future<JsonArray> getAll(String tableName)
    {
        Promise<JsonArray> promise = Promise.promise();

        Main.vertx.executeBlocking(getAll ->
        {
            try
            {
                String query = "SELECT * FROM " + tableName;

                client.preparedQuery(query).execute(execute ->
                {
                    if (execute.succeeded())
                    {
                        var rows = execute.result();

                        var response = new JsonArray();

                        for (Row row : rows)
                        {
                            var json = new JsonObject();

                            for (int i = 0; i < row.size(); i++)
                            {
                                var columnName = row.getColumnName(i);

                                var value = row.getValue(i);

                                json.put(columnName, value);
                            }
                            response.add(json);
                        }

                        getAll.complete(response);
                    }
                    else
                    {
                        getAll.fail(execute.cause());
                    }
                });
            }
            catch (Exception exception)
            {
                getAll.fail(exception);
            }
        }, false,result ->
        {
            if (result.succeeded())
            {
                promise.complete((JsonArray) result.result());
            }
            else
            {
                promise.fail(result.cause());
            }
        });
        return promise.future();
    }


    public Future<JsonObject> get(String tableName, List<String> columns, JsonObject filter)
    {
        var promise = Promise.<JsonObject>promise();

        var selectClause = columns.isEmpty() ? "*" : String.join(", ", columns);

        var whereClause = new StringBuilder();

        var values = new ArrayList<>();

        filter.forEach(entry ->
        {
            if (!whereClause.isEmpty())
            {
                whereClause.append(" AND ");
            }

            whereClause.append(entry.getKey()).append(" = $").append(values.size() + 1);

            values.add(entry.getValue());
        });

        client.preparedQuery("SELECT " + selectClause + " FROM " + tableName + " WHERE " + whereClause)
                .execute(Tuple.from(values), execute ->
                {
                    if (execute.succeeded())
                    {
                        var rows = execute.result();

                        int count = rows.rowCount();

                        if(count==1)
                        {
                            var row = rows.iterator().next();

                            // Convert the Row into a JsonObject
                            var response = new JsonObject();

                            for (int i = 0; i < row.size(); i++)
                            {
                                var fieldName = row.getColumnName(i);

                                var fieldValue = row.getValue(i);

                                response.put(fieldName, fieldValue);
                            }
                            promise.complete(response);
                        }
                        else if (count>1)
                        {
                            var rowsArray = new JsonArray();

                            for (Row row : rows)
                            {
                                JsonObject rowObject = new JsonObject();

                                for (int i = 0; i < row.size(); i++)
                                {
                                    var fieldName = row.getColumnName(i);

                                    var fieldValue = row.getValue(i);

                                    rowObject.put(fieldName, fieldValue);
                                }
                                rowsArray.add(rowObject);
                            }

                            promise.complete(new JsonObject()
                                    .put("data", rowsArray));
                        }
                        else
                        {
                            promise.complete(new JsonObject().put("error", "No matching record found"));
                        }
                    }
                    else
                    {
                        promise.fail(execute.cause().getMessage());
                    }
                });

        return promise.future();
    }

    public Future<Boolean> update(String tableName, JsonObject data, JsonObject filter)
    {
        var promise = Promise.<Boolean>promise();

        Main.vertx.executeBlocking(update ->
        {
            try
            {
                var setClause = new StringBuilder();

                var whereClause = new StringBuilder();

                var values = new ArrayList<>();

                // Construct the SET clause
                data.forEach(entry ->
                {
                    if (!setClause.isEmpty())
                    {
                        setClause.append(", ");
                    }
                    setClause.append(entry.getKey()).append(" = $").append(values.size() + 1);

                    values.add(entry.getValue());
                });

                // Construct the WHERE clause
                filter.forEach(entry ->
                {
                    if (!whereClause.isEmpty())
                    {
                        whereClause.append(" AND ");
                    }
                    whereClause.append(entry.getKey()).append(" = $").append(values.size() + 1);

                    values.add(entry.getValue());
                });

                // Prepare and execute the query
                var query = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;

                client.preparedQuery(query).execute(Tuple.from(values), execute ->
                {
                    if (execute.succeeded())
                    {
                        if (execute.result().rowCount() > 0)
                        {
                            update.complete(true); // Update succeeded
                        }
                        else
                        {
                            update.fail("No matching rows found"); // No rows updated
                        }
                    }
                    else
                    {
                        update.fail(execute.cause());
                    }
                });
            }
            catch (Exception exception)
            {
                update.fail(exception); // Handle any unexpected exceptions
            }
        }, false,result ->
        {
            if (result.succeeded())
            {
                promise.complete((Boolean) result.result());
            }
            else
            {
                promise.fail(result.cause());
            }
        });
        return promise.future();
    }
}
