/*!
* Copyright 2010 - 2013 Pentaho Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

package org.apache.hadoop.hive.jdbc;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.service.HiveClient;
import org.apache.hadoop.hive.service.HiveInterface;
import org.apache.hadoop.hive.service.HiveServer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;

/**
 * HiveConnection.
 *
 */
public class HiveConnection implements java.sql.Connection {
  private final JdbcSessionState session;
  private TTransport transport;
  private HiveInterface client;
  private boolean isClosed = true;
  private SQLWarning warningChain = null;

  private static final String URI_PREFIX = "jdbc:hive://";

  /**
   * TODO: - parse uri (use java.net.URI?).
   */
  public HiveConnection(String uri, Properties info) throws SQLException {
    session = new JdbcSessionState(new HiveConf(SessionState.class));
    session.in = null;
    session.out = null;
    session.err = null;
    SessionState.start(session);
    String originalUri = uri;

    if (!uri.startsWith(URI_PREFIX)) {
      throw new SQLException("Invalid URL: " + uri, "08S01");
    }

    // remove prefix
    uri = uri.substring(URI_PREFIX.length());

    // If uri is not specified, use local mode.
    if (uri.isEmpty()) {
      try {
        client = new HiveServer.HiveServerHandler();
      } catch (MetaException e) {
        throw new SQLException("Error accessing Hive metastore: "
            + e.getMessage(), "08S01");
      }
    } else {
      // parse uri
      // form: hostname:port/databasename
      String[] parts = uri.split("/");
      String[] hostport = parts[0].split(":");
      int port = 10000;
      String host = hostport[0];
      try {
        port = Integer.parseInt(hostport[1]);
      } catch (Exception e) {
        if(hostport.length < 2) {
          throw new SQLException("Invalid port in host: " + parts[0]);
        } else {
          throw new SQLException("Invalid port: " + hostport[1]);
        }
      }
      transport = new TSocket(host, port);
      TProtocol protocol = new TBinaryProtocol(transport);
      client = new HiveClient(protocol);
      try {
        transport.open();
      } catch (TTransportException e) {
        throw new SQLException("Could not establish connecton to "
            + originalUri + ": " + e.getMessage(), "08S01");
      }
    }
    isClosed = false;
    configureConnection();
  }

  private void configureConnection() throws SQLException {
    Statement stmt = createStatement();
    stmt.execute(
        "set hive.fetch.output.serde = org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe");
    stmt.close();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#clearWarnings()
   */

  public void clearWarnings() throws SQLException {
    warningChain = null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#close()
   */

  public void close() throws SQLException {
    try {
      if (transport != null) {
        transport.close();
      }
    } finally {
      isClosed = true;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#commit()
   */

  public void commit() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - commit()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createArrayOf(java.lang.String,
   * java.lang.Object[])
   */

  public Array createArrayOf(String arg0, Object[] arg1) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createArrayOf");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createBlob()
   */

  public Blob createBlob() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createBlob");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createClob()
   */

  public Clob createClob() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createClob");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createNClob()
   */

  public NClob createNClob() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createNClob");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createSQLXML()
   */

  public SQLXML createSQLXML() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createSQLXML");
  }

  /**
   * Creates a Statement object for sending SQL statements to the database.
   * 
   * @throws SQLException
   *           if a database access error occurs.
   * @see java.sql.Connection#createStatement()
   */

  public Statement createStatement() throws SQLException {
    if (isClosed) {
      throw new SQLException("Can't create Statement, connection is closed");
    }
    return new HiveStatement(session, client);
  }

  /**
   * Constructs a Statement of the given type and result set concurrency.
   * 
   * @param resultSetType - one of the following ResultSet constants: ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, or ResultSet.TYPE_SCROLL_SENSITIVE 
   * @param resultSetConcurrency - one of the following ResultSet constants: ResultSet.CONCUR_READ_ONLY or ResultSet.CONCUR_UPDATABLE
   */
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    if (isClosed) {
      throw new SQLException("Can't create Statement, connection is closed ");
    }
    
    if(resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
      throw new SQLException(
          "Invalid parameter to createStatement() only TYPE_FORWARD_ONLY is supported");
    }
    
    if(resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
      throw new SQLException(
          "Invalid parameter to createStatement() only CONCUR_READ_ONLY is supported");
    }
    return createStatement();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createStatement(int, int, int)
   */

  public Statement createStatement(int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - createStatement(int, int, int");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#createStruct(java.lang.String, java.lang.Object[])
   */

  public Struct createStruct(String typeName, Object[] attributes)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported -  createStruct(String, Object[])");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getAutoCommit()
   */

  public boolean getAutoCommit() throws SQLException {
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getCatalog()
   */

  public String getCatalog() throws SQLException {
    return "";
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getClientInfo()
   */

  public Properties getClientInfo() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - getClientInfo()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getClientInfo(java.lang.String)
   */

  public String getClientInfo(String name) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - getClientInfo(String name)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getHoldability()
   */

  public int getHoldability() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - getHoldability()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getMetaData()
   */

  public DatabaseMetaData getMetaData() throws SQLException {
    return new HiveDatabaseMetaData(client);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getTransactionIsolation()
   */

  public int getTransactionIsolation() throws SQLException {
    return Connection.TRANSACTION_NONE;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getTypeMap()
   */

  public Map<String, Class<?>> getTypeMap() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - getTypeMap()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#getWarnings()
   */

  public SQLWarning getWarnings() throws SQLException {
    return warningChain;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#isClosed()
   */

  public boolean isClosed() throws SQLException {
    return isClosed;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#isReadOnly()
   */

  public boolean isReadOnly() throws SQLException {
    return false;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#isValid(int)
   */

  public boolean isValid(int timeout) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - isValid(int timeout)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#nativeSQL(java.lang.String)
   */

  public String nativeSQL(String sql) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - nativeSQL(String sql)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareCall(java.lang.String)
   */

  public CallableStatement prepareCall(String sql) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareCall(String sql)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
   */

  public CallableStatement prepareCall(String sql, int resultSetType,
      int resultSetConcurrency) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareCall(String , int, int)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
   */

  public CallableStatement prepareCall(String sql, int resultSetType,
      int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareCall(String, int, int, int)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String)
   */

  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return new HivePreparedStatement(session, client, sql);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String, int)
   */

  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
      throws SQLException {
    return new HivePreparedStatement(session, client, sql);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
   */

  public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareStatement(String, int[])");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String,
   * java.lang.String[])
   */

  public PreparedStatement prepareStatement(String sql, String[] columnNames)
      throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareStatement(String, String[])");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
   */

  public PreparedStatement prepareStatement(String sql, int resultSetType,
      int resultSetConcurrency) throws SQLException {
    return new HivePreparedStatement(session, client, sql);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#prepareStatement(java.lang.String, int, int, int)
   */

  public PreparedStatement prepareStatement(String sql, int resultSetType,
      int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - prepareStatement(String, int, int, int)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
   */

  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - releaseSavepoint(Savepoint savepoint)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#rollback()
   */

  public void rollback() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - rollback()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#rollback(java.sql.Savepoint)
   */

  public void rollback(Savepoint savepoint) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - rollback(Savepoint savepoint)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setAutoCommit(boolean)
   */

  public void setAutoCommit(boolean autoCommit) throws SQLException {
    // getAutoCommit() always returns true - so 'true' is fine for
    // consistency's sake
    if(!autoCommit) {
      throw new SQLException("Method not supported - setAutoCommit(false)");
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setCatalog(java.lang.String)
   */

  public void setCatalog(String catalog) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setCatalog(String catalog)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setClientInfo(java.util.Properties)
   */

  public void setClientInfo(Properties properties)
      throws SQLClientInfoException {
    // TODO Auto-generated method stub
    throw new SQLClientInfoException("Method not supported - setClientInfo(Properties)", null);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setClientInfo(java.lang.String, java.lang.String)
   */

  public void setClientInfo(String name, String value)
      throws SQLClientInfoException {
    // TODO Auto-generated method stub
    throw new SQLClientInfoException("Method not supported - setClientInfo(String, String)", null);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setHoldability(int)
   */

  public void setHoldability(int holdability) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setHoldability(int holdability)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setReadOnly(boolean)
   */

  public void setReadOnly(boolean readOnly) throws SQLException {
    // TODO Auto-generated method stub
    //JD    
    //    throw new SQLException("Method not supported - setReadOnly(boolean readOnly)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setSavepoint()
   */

  public Savepoint setSavepoint() throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setSavepoint()");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setSavepoint(java.lang.String)
   */

  public Savepoint setSavepoint(String name) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setSavepoint(String name)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setTransactionIsolation(int)
   */

  public void setTransactionIsolation(int level) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setTransactionIsolation(int level)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Connection#setTypeMap(java.util.Map)
   */

  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - setTypeMap(Map<String, Class<?>> map)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Wrapper#isWrapperFor(java.lang.Class)
   */

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - isWrapperFor(Class<?> iface)");
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.sql.Wrapper#unwrap(java.lang.Class)
   */

  public <T> T unwrap(Class<T> iface) throws SQLException {
    // TODO Auto-generated method stub
    throw new SQLException("Method not supported - unwrap(Class<T> iface)");
  }

}
