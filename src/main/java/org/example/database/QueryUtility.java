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

import static org.example.util.Helper.insertInMap;

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

    private static PgPool client;

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

//                            insertInMap(tableName,id,data);

                            promise.complete(id);
                        }
                        else
                        {
                            promise.fail("Some problem");
                        }
                    }
                    else
                    {
                        promise.fail(execute.cause().getMessage());
                    }
                });
        return promise.future();
    }

    public Future<Void> delete(String tableName, String column, Long id)
    {
        var promise = Promise.<Void>promise();

        client.preparedQuery("DELETE FROM "+ tableName + " WHERE " + column + " = $1")
                .execute(Tuple.of(id), execute ->{
                    if(execute.succeeded())
                    {
                        if(execute.result().rowCount()>0)
                        {
                            promise.complete();
                        }
                        else
                        {
                            promise.fail("Information not found");
                        }
                    }
                    else
                    {
                        promise.fail(execute.cause());
                    }
                });

        return promise.future();
    }

    public Future<JsonArray> getAll(String tableName)
    {
        var promise = Promise.<JsonArray>promise();

        client.preparedQuery("SELECT * FROM "+ tableName)
                .execute(execute->{
                    if(execute.succeeded())
                    {
                        var rows = execute.result();

                        var response = new JsonArray();

                        for(Row row : rows)
                        {
                            var json = new JsonObject();

                            for(int i=0;i<row.size();i++)
                            {
                                var columnName = row.getColumnName(i);

                                var value = row.getValue(i);

                                json.put(columnName, value);
                            }
                            response.add(json);
                        }
                        promise.complete(response);

                    }
                    else
                    {
                        promise.fail(execute.cause());
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

        // Execute the query
        client.preparedQuery("UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause)
                .execute(Tuple.from(values), execute ->
                {
                    if (execute.succeeded())
                    {
                        if (execute.result().rowCount() > 0)
                        {
                            promise.complete(true); // Update succeeded
                        }
                        else
                        {
                            promise.fail("No matching rows found"); // No rows updated
                        }
                    }
                    else
                    {
                        promise.fail(execute.cause());
                    }
                });

        return promise.future();
    }

}
