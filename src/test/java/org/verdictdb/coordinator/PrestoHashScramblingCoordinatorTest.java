package org.verdictdb.coordinator;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.verdictdb.category.PrestoTests;
import org.verdictdb.commons.DatabaseConnectionHelpers;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.connection.DbmsQueryResult;
import org.verdictdb.connection.JdbcConnection;
import org.verdictdb.exception.VerdictDBDbmsException;
import org.verdictdb.exception.VerdictDBException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category(PrestoTests.class)
public class PrestoHashScramblingCoordinatorTest {

  private static Connection prestoConn;

  private static Statement prestoStmt;

  private static final String PRESTO_HOST;

  private static final String PRESTO_CATALOG;

  private static final String PRESTO_USER;

  private static final String PRESTO_PASSWORD;

  private static final String PRESTO_SCHEMA =
      "coordinator_test_" + RandomStringUtils.randomAlphanumeric(8).toLowerCase();

  static {
    PRESTO_HOST = "localhost:8080";
    PRESTO_CATALOG = "memory";
    PRESTO_USER = "root";
    PRESTO_PASSWORD = "";
    //    PRESTO_HOST = System.getenv("VERDICTDB_TEST_PRESTO_HOST");
    //    PRESTO_CATALOG = System.getenv("VERDICTDB_TEST_PRESTO_CATALOG");
    //    PRESTO_USER = System.getenv("VERDICTDB_TEST_PRESTO_USER");
    //    PRESTO_PASSWORD = System.getenv("VERDICTDB_TEST_PRESTO_PASSWORD");
  }

  @BeforeClass
  public static void setupPrestoDatabase()
      throws SQLException, VerdictDBDbmsException, IOException {
    //    String prestoConnectionString =
    //        String.format("jdbc:presto://%s/%s/default", PRESTO_HOST, PRESTO_CATALOG);
    String prestoConnectionString = String.format("jdbc:presto://%s/memory", PRESTO_HOST);

    // Clean leftover schemas
    Connection conn = DriverManager.getConnection(prestoConnectionString, "root", "");
    ResultSet rs = conn.createStatement().executeQuery("SHOW SCHEMAS IN memory");
    while (rs.next()) {
      String schema = rs.getString(1);
      if (!schema.equalsIgnoreCase(PRESTO_SCHEMA) && schema.startsWith("coordinator_test_")) {
        ResultSet rs2 =
            conn.createStatement().executeQuery(String.format("SHOW TABLES IN memory.%s", schema));
        while (rs2.next()) {
          String table = rs2.getString(1);
          conn.createStatement().execute(String.format("DROP TABLE memory.%s.%s", schema, table));
        }
        conn.createStatement().execute(String.format("DROP SCHEMA IF EXISTS memory.%s", schema));
      }
    }

    prestoConn =
        DatabaseConnectionHelpers.setupPrestoInMemory(
            prestoConnectionString, PRESTO_USER, PRESTO_PASSWORD, PRESTO_SCHEMA);
    prestoStmt = prestoConn.createStatement();
  }

  @AfterClass
  public static void tearDown() throws SQLException {
    ResultSet rs =
        prestoConn
            .createStatement()
            .executeQuery(String.format("SHOW TABLES IN memory.%s", PRESTO_SCHEMA));
    while (rs.next()) {
      prestoStmt.execute(
          String.format("DROP TABLE IF EXISTS memory.%s.%s", PRESTO_SCHEMA, rs.getString(1)));
    }
    rs.close();
    prestoStmt.execute(String.format("DROP SCHEMA IF EXISTS memory.%s", PRESTO_SCHEMA));
    prestoStmt.close();
    prestoConn.close();
  }

  @Test
  public void sanityCheck() throws VerdictDBDbmsException {
    DbmsConnection conn = JdbcConnection.create(prestoConn);
    DbmsQueryResult result =
        conn.execute(String.format("select * from %s.lineitem", PRESTO_SCHEMA));
    int rowCount = 0;
    while (result.next()) {
      rowCount++;
    }
    assertEquals(1000, rowCount);
  }

  @Test
  public void testScramblingCoordinatorLineitem() throws VerdictDBException {
    testScramblingCoordinator("lineitem", "l_orderkey");
  }

  @Test
  public void testScramblingCoordinatorOrders() throws VerdictDBException {
    testScramblingCoordinator("orders", "o_orderkey");
  }

  public void testScramblingCoordinator(String tablename, String columnname)
      throws VerdictDBException {
    DbmsConnection conn = JdbcConnection.create(prestoConn);

    String scrambleSchema = PRESTO_SCHEMA;
    String scratchpadSchema = PRESTO_SCHEMA;
    long blockSize = 100;
    ScramblingCoordinator scrambler =
        new ScramblingCoordinator(conn, scrambleSchema, scratchpadSchema, blockSize);

    // perform scrambling
    String originalSchema = PRESTO_SCHEMA;
    String originalTable = tablename;
    String scrambledTable = tablename + "_scrambled";
    conn.execute(String.format("drop table if exists %s.%s", PRESTO_SCHEMA, scrambledTable));
    scrambler.scramble(
        originalSchema, originalTable, originalSchema, scrambledTable, "hash", columnname);

    // tests
    List<Pair<String, String>> originalColumns = conn.getColumns(PRESTO_SCHEMA, originalTable);
    List<Pair<String, String>> columns = conn.getColumns(PRESTO_SCHEMA, scrambledTable);
    for (int i = 0; i < originalColumns.size(); i++) {
      assertEquals(originalColumns.get(i).getLeft(), columns.get(i).getLeft());
      assertEquals(originalColumns.get(i).getRight(), columns.get(i).getRight());
    }
    assertEquals(originalColumns.size() + 2, columns.size());

    List<String> partitions = conn.getPartitionColumns(PRESTO_SCHEMA, scrambledTable);
    // This is invalid with 'memory' catalog
    //    assertEquals(Arrays.asList("verdictdbblock"), partitions);

    DbmsQueryResult result1 =
        conn.execute(String.format("select count(*) from %s.%s", PRESTO_SCHEMA, originalTable));
    DbmsQueryResult result2 =
        conn.execute(String.format("select count(*) from %s.%s", PRESTO_SCHEMA, scrambledTable));
    result1.next();
    result2.next();
    assertEquals(result1.getInt(0), result2.getInt(0));

    DbmsQueryResult result =
        conn.execute(
            String.format(
                "select min(verdictdbblock), max(verdictdbblock) from %s.%s",
                PRESTO_SCHEMA, scrambledTable));
    result.next();
    assertEquals(0, result.getInt(0));
    assertEquals((int) Math.ceil(result2.getInt(0) / (float) blockSize) - 1, result.getInt(1));
  }
}
