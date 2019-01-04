/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.routing.type.standard;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

public final class SubQueryRouteTest extends AbstractSQLRouteTest {
    
    @Test(expected = IllegalStateException.class)
    public void assertOneTableError() {
        String sql = "select (select max(id) from t_order b where b.user_id =? ) from t_order a where user_id = ? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(3);
        parameters.add(2);
        route(sql, parameters);
    }
    
    @Test
    public void assertOneTable() {
        String sql = "select (select max(id) from t_order b where b.user_id = ? and b.user_id = a.user_id) from t_order a where user_id = ? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test
    public void assertBindingTable() {
        String sql = "select (select max(id) from t_order_item b where b.user_id = ?) from t_order a where user_id = ? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test
    public void assertNotShardingTable() {
        String sql = "select (select max(id) from t_user b where b.id = ?) from t_user a where id = ? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertBindingTableWithDifferentValue() {
        String sql = "select (select max(id) from t_order_item b where b.user_id = ? ) from t_order a where user_id = ? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(3);
        route(sql, parameters);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertTwoTableWithDifferentOperator() {
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(3);
        parameters.add(1);
        String sql = "select (select max(id) from t_order_item b where b.user_id in(?,?)) from t_order a where user_id = ? ";
        route(sql, parameters);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertTwoTableWithIn() {
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(3);
        parameters.add(1);
        parameters.add(3);
        String sql = "select (select max(id) from t_order_item b where b.user_id in(?,?)) from t_order a where user_id in(?,?) ";
        route(sql, parameters);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertSubQueryInSubQueryError() {
        List<Object> parameters = new LinkedList<>();
        parameters.add(11);
        parameters.add(1);
        parameters.add(1);
        parameters.add(1);
        String sql = "select (select status from t_order b where b.user_id =? and status = (select status from t_order b where b.user_id =?)) as c from t_order a "
                + "where status = (select status from t_order b where b.user_id =? and status = (select status from t_order b where b.user_id =?))";
        route(sql, parameters);
    }
    
    @Test
    public void assertSubQueryInSubQuery() {
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(1);
        parameters.add(1);
        parameters.add(1);
        String sql = "select (select status from t_order b where b.user_id =? and status = (select status from t_order b where b.user_id =?)) as c from t_order a "
                + "where status = (select status from t_order b where b.user_id =? and status = (select status from t_order b where b.user_id =?))";
        route(sql, parameters);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertSubQueryInFromError() {
        String sql = "select status from t_order b join (select user_id,status from t_order b where b.user_id =?) c on b.user_id = c.user_id where b.user_id =? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(11);
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test
    public void assertSubQueryInFrom() {
        String sql = "select status from t_order b join (select user_id,status from t_order b where b.user_id =?) c on b.user_id = c.user_id where b.user_id =? ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test
    public void assertSubQueryForAggregation() {
        String sql = "select count(*) from t_order where c.user_id = (select user_id from t_order where user_id =?) ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        route(sql, parameters);
    }
    
    @Test
    public void assertSubQueryForBinding() {
        String sql = "select count(*) from t_order where user_id = (select user_id from t_order_item where user_id =?) ";
        List<Object> parameters = new LinkedList<>();
        parameters.add(1);
        route(sql, parameters);
    }
}
