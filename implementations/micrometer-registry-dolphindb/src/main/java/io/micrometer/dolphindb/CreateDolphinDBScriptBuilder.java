package io.micrometer.dolphindb;

import io.micrometer.common.lang.Nullable;

public class CreateDolphinDBScriptBuilder {

    private final static String db_PATH = "dbPath = '%s'\n";

    private final static String DROP_EXISTING_DATABASE = "if(existsDatabase(dbPath)) dropDatabase(dbPath)\n";

    private final static String TABLE_SCHEMA = "t = table(100:0, `metric`timestamp`value`tags, [STRING, TIMESTAMP, DOUBLE, STRING])\n";
    private final static String CREATE_DATABASE = "db = database(dbPath, partitionType = %s, partitionScheme = 2023.04.25..2023.05.25, engine = '%s')\n";

    private final static String CREATE_TABLE = "pt = db.createPartitionedTable(t, `pt, `timestamp, sortColumns = `metric`tags`timestamp)\n";

    private final String directory;
    private final String[] ScriptClauses = new String[2];

    CreateDolphinDBScriptBuilder(String directory) {
        if (isEmpty(directory)) {
            throw new IllegalArgumentException("The directory cannot be null or empty");
        }
        this.directory = directory;
    }

    CreateDolphinDBScriptBuilder testDatabaseDirectory(@Nullable String directory) {
        if(!isEmpty(directory)) {
            ScriptClauses[0] = String.format(DROP_EXISTING_DATABASE, directory, directory);
        }
        return this;
    }

    CreateDolphinDBScriptBuilder setCreateDatabase(@Nullable String type, @Nullable String schema, @Nullable String engine) {
        if(!(isEmpty(type)||isEmpty(schema)||isEmpty(engine))) {
            ScriptClauses[1] = String.format(CREATE_DATABASE, type, engine);
        }
        return this;
    }

    String build() {
        StringBuilder scriptStringBuilder = new StringBuilder(String.format(db_PATH, directory));
        String ScriptClause = scriptStringBuilder
                .append(ScriptClauses[0])
                .append(ScriptClauses[1])
                .append(TABLE_SCHEMA)
//                .append(ScriptClauses[2])
                .append(CREATE_TABLE)
                .toString();
        return ScriptClause;
    }
    private boolean isEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }

}
