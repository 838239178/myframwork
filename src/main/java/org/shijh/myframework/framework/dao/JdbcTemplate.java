package org.shijh.myframework.framework.dao;


import org.shijh.myframework.framework.Assembler;
import org.shijh.myframework.framework.ResultMap;
import org.shijh.myframework.framework.annotation.Autowired;
import org.shijh.myframework.framework.annotation.Component;
import org.shijh.myframework.framework.util.Str;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

@Component("jdbcTemplate")
public class JdbcTemplate {

    @Autowired
    private ConnectionPool connectionPool;

    public JdbcTemplate() {
    }

    private Connection getConnection() {
        return connectionPool.getConnection();
    }

    private Map<String, Object> getResultMap(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < metaData.getColumnCount(); ++i) {
            String columnName = metaData.getColumnName(i + 1);
            map.put(columnName, resultSet.getString(columnName));
        }
        return map;
    }

    /**
     * 普通sql封装， sql使用 ？ 占位符
     */
    private PreparedStatement simplePopulate(Connection connection, String sql, Object[] args) throws SQLException, IllegalAccessException {
        PreparedStatement statement = connection.prepareStatement(sql);
        int i = 0;
        try {
            for (i = 0; i < args.length; i++) {
                setStatementParam(statement,i+1, args[i]);
            }
        } catch (NoSuchMethodException | InvocationTargetException e) {
            throw new SQLException("不支持的参数类型->" + args[i].getClass().getName(), e);
        }
        return statement;
    }

    private String getStatementSetterName(Object param) {
        String typeName = param.getClass().getSimpleName();
        String fstChar = typeName.substring(0, 1).toUpperCase();
        String otherChars = typeName.substring(1);
        return "set" + fstChar + otherChars;
    }

    @SuppressWarnings("unchecked")
    private void setStatementParam(PreparedStatement statement, int pramIndex, Object param) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException{
        String statementMethodName = getStatementSetterName(param);
        Class<?> pClass = param.getClass();
        if (param instanceof Number) {
            pClass = Assembler.primitiveClass((Class<? extends Number>) pClass);
        }
        if (statementMethodName.equals("setInteger")) {
            statementMethodName = "setInt";
        }
        Method method = statement.getClass().getDeclaredMethod(statementMethodName, int.class, pClass);
        method.setAccessible(true);
        method.invoke(statement,pramIndex, param);
    }

    /**
     * 类似Mybatis的Sql封装， 使用 #{} 占位符
     */
    private PreparedStatement beanPopulate(Connection connection, String sql, Object bean) throws SQLException, NoSuchFieldException, IllegalAccessException {
        String regex = "#\\{([a-zA-Z0-9]+)}";
        String[] params = Str.match(regex, sql);

        System.out.println(Arrays.toString(params));

        String afterSql = sql.replaceAll(regex, "?");

        System.out.println(afterSql);

        PreparedStatement statement = connection.prepareStatement(afterSql);
        Object o = null;
        try {
            for (int i = 0; i < params.length; i++) {
                String param = params[i];
                Field field = bean.getClass().getDeclaredField(param);
                field.setAccessible(true);
                o = field.get(bean);
                setStatementParam(statement,i+1, o);
            }
        } catch (NoSuchMethodException | InvocationTargetException e) {
            throw new SQLException("不支持的参数类型->" + o.getClass().getName(), e);
        }
        return statement;
    }

    private PreparedStatement getStatement(String sql, Object[] args, boolean isBean) {
        Connection connection = getConnection();
        try {
            if (!isBean) {
                return simplePopulate(connection, sql, args);
            } else if (args.length > 0) {
                return beanPopulate(connection, sql, args[0]);
            }
            return connection.prepareStatement(sql);
        } catch (SQLException throwables) {
            System.out.println("错误的sql:" + sql);
            throwables.printStackTrace();
        } catch (IllegalAccessException | NoSuchFieldException ie) {
            System.out.println("参数封装错误");
            ie.printStackTrace();
        } finally {
            connectionPool.returnConnection(connection);
        }
        return null;
    }

    private int update(String sql, Object[] args, boolean isBean) {
        try (PreparedStatement statement = getStatement(sql, args, isBean)) {
            assert statement != null;
            return statement.executeUpdate();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> queryList(String sql, boolean isBean, boolean isForObject, ResultMap resultMap, Object... args) {
        ResultSet resultSet = null;
        List<T> resultList = new ArrayList<>();
        PreparedStatement statement = null;
        try {
            statement = getStatement(sql, args, isBean);
            assert statement != null;
            resultSet = statement.executeQuery();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        try {
            while (resultSet.next()) {
                Map<String, Object> rm = getResultMap(resultSet);
                Object result = resultMap.invoke(rm);
                resultList.add((T) result);
                if (isForObject) break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            ConnectionManager.closeResultSet(resultSet);
            ConnectionManager.closeStatement(statement);
        }
        return resultList;
    }

    private int doUpdate(String sql, boolean isBean, Object... args) {
        return update(sql, args, isBean);
    }

    /**
     * 执行更新操作
     *
     * @param sql  使用<B>?</B>作为占位符！！
     * @param args 参数
     * @return effect rows
     */
    public int executeUpdate(String sql, Object... args) {
        return doUpdate(sql, false, args);
    }

    /**
     * 执行更新操作
     *
     * @param sql  使用<B>#{prop}</B>作为占位符！！
     * @param bean java bean
     * @return effect rows
     */
    public int executeUpdate(Object bean, String sql) {
        return doUpdate(sql, true, bean);
    }

    /**
     * 执行查询操作，返回单个对象
     *
     * @param sql  使用<B>#{prop}</B>作为占位符！！
     * @param bean java bean
     * @return T
     */
    @SuppressWarnings("unchecked")
    public <T> T queryObject(String sql, Object bean, ResultMap resultMap) {
        return (T) queryList(sql, true, true, resultMap, bean).get(0);
    }

    /**
     * 执行查询操作，返回单个对象
     *
     * @param sql  使用<B>?</B>作为占位符！！
     * @param args 参数
     * @return T
     */
    @SuppressWarnings("unchecked")
    public <T> T queryObject(String sql, ResultMap resultMap, Object... args) {
        return (T) queryList(sql, false, true, resultMap, args).get(0);
    }

    /**
     * 执行查询操作，返回多个对象的结果集
     *
     * @param sql  使用<B>?</B>作为占位符！！
     * @param args 参数
     * @return {@link List}
     */
    public <T> List<T> queryList(String sql, ResultMap resultMap, Object... args) {
        return queryList(sql, false, false, resultMap, args);
    }

    /**
     * 执行查询操作，返回多个对象的结果集
     *
     * @param sql  使用<B>#{prop}</B>作为占位符！！
     * @param bean java bean
     * @return {@link List}
     */
    public <T> List<T> queryList(String sql, Object bean, ResultMap resultMap) {
        return queryList(sql, true, false, resultMap, bean);
    }

}