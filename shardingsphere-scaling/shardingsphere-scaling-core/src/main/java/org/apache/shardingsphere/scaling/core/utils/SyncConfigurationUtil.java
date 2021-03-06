/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.scaling.core.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.infra.config.datasource.DataSourceConfiguration;
import org.apache.shardingsphere.scaling.core.config.DumperConfiguration;
import org.apache.shardingsphere.scaling.core.config.ImporterConfiguration;
import org.apache.shardingsphere.scaling.core.config.JDBCDataSourceConfiguration;
import org.apache.shardingsphere.scaling.core.config.JobConfiguration;
import org.apache.shardingsphere.scaling.core.config.ScalingConfiguration;
import org.apache.shardingsphere.scaling.core.config.ShardingSphereJDBCConfiguration;
import org.apache.shardingsphere.scaling.core.config.SyncConfiguration;
import org.apache.shardingsphere.sharding.algorithm.sharding.inline.InlineExpressionParser;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.ComplexShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.ShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sharding.rule.TableRule;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * Sync configuration Util.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SyncConfigurationUtil {
    
    /**
     * Split Scaling configuration to Sync configurations.
     *
     * @param scalingConfiguration scaling configuration
     * @return list of sync configurations
     */
    public static Collection<SyncConfiguration> toSyncConfigurations(final ScalingConfiguration scalingConfiguration) {
        Collection<SyncConfiguration> result = new LinkedList<>();
        ShardingSphereJDBCConfiguration sourceConfiguration = getSourceConfiguration(scalingConfiguration);
        Map<String, DataSourceConfiguration> sourceDataSource = ConfigurationYamlConverter.loadDataSourceConfigurations(sourceConfiguration.getDataSource());
        ShardingRuleConfiguration sourceRule = ConfigurationYamlConverter.loadShardingRuleConfiguration(sourceConfiguration.getRule());
        Map<String, Map<String, String>> dataSourceTableNameMap = toDataSourceTableNameMap(sourceRule, sourceDataSource.keySet());
        Optional<ShardingRuleConfiguration> targetRule = getTargetRuleConfiguration(scalingConfiguration);
        filterByShardingDataSourceTables(dataSourceTableNameMap, scalingConfiguration.getJobConfiguration());
        for (Entry<String, Map<String, String>> entry : dataSourceTableNameMap.entrySet()) {
            DumperConfiguration dumperConfiguration = createDumperConfiguration(entry.getKey(), sourceDataSource.get(entry.getKey()), entry.getValue());
            ImporterConfiguration importerConfiguration = createImporterConfiguration(scalingConfiguration, targetRule.orElse(sourceRule));
            importerConfiguration.setRetryTimes(scalingConfiguration.getJobConfiguration().getRetryTimes());
            result.add(new SyncConfiguration(scalingConfiguration.getJobConfiguration().getConcurrency(), dumperConfiguration, importerConfiguration));
        }
        return result;
    }
    
    private static ShardingSphereJDBCConfiguration getSourceConfiguration(final ScalingConfiguration scalingConfiguration) {
        org.apache.shardingsphere.scaling.core.config.DataSourceConfiguration dataSourceConfiguration = scalingConfiguration.getRuleConfiguration().getSource().toTypedDataSourceConfiguration();
        Preconditions.checkArgument(dataSourceConfiguration instanceof ShardingSphereJDBCConfiguration, "Only support ShardingSphere source data source.");
        return (ShardingSphereJDBCConfiguration) dataSourceConfiguration;
    }
    
    private static Optional<ShardingRuleConfiguration> getTargetRuleConfiguration(final ScalingConfiguration scalingConfiguration) {
        org.apache.shardingsphere.scaling.core.config.DataSourceConfiguration dataSourceConfiguration = scalingConfiguration.getRuleConfiguration().getTarget().toTypedDataSourceConfiguration();
        if (dataSourceConfiguration instanceof ShardingSphereJDBCConfiguration) {
            return Optional.of(ConfigurationYamlConverter.loadShardingRuleConfiguration(((ShardingSphereJDBCConfiguration) dataSourceConfiguration).getRule()));
        }
        return Optional.empty();
    }
    
    private static void filterByShardingDataSourceTables(final Map<String, Map<String, String>> dataSourceTableNameMap, final JobConfiguration jobConfiguration) {
        if (null == jobConfiguration.getShardingTables()) {
            return;
        }
        Map<String, Set<String>> shardingDataSourceTableMap = toDataSourceTableNameMap(getShardingDataSourceTables(jobConfiguration));
        dataSourceTableNameMap.entrySet().removeIf(entry -> !shardingDataSourceTableMap.containsKey(entry.getKey()));
        for (Entry<String, Map<String, String>> entry : dataSourceTableNameMap.entrySet()) {
            filterByShardingTables(entry.getValue(), shardingDataSourceTableMap.get(entry.getKey()));
        }
    }
    
    private static String getShardingDataSourceTables(final JobConfiguration jobConfiguration) {
        if (jobConfiguration.getShardingItem() >= jobConfiguration.getShardingTables().length) {
            return "";
        }
        return jobConfiguration.getShardingTables()[jobConfiguration.getShardingItem()];
    }
    
    private static void filterByShardingTables(final Map<String, String> fullTables, final Set<String> shardingTables) {
        fullTables.entrySet().removeIf(entry -> !shardingTables.contains(entry.getKey()));
    }
    
    private static Map<String, Set<String>> toDataSourceTableNameMap(final String shardingDataSourceTables) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String each : new InlineExpressionParser(shardingDataSourceTables).splitAndEvaluate()) {
            String[] table = each.split("\\.");
            if (!result.containsKey(table[0])) {
                result.put(table[0], new HashSet<>());
            }
            result.get(table[0]).add(table[1]);
        }
        return result;
    }
    
    private static Map<String, Map<String, String>> toDataSourceTableNameMap(final ShardingRuleConfiguration shardingRuleConfig, final Collection<String> dataSourceNames) {
        ShardingRule shardingRule = new ShardingRule(shardingRuleConfig, dataSourceNames);
        Map<String, Map<String, String>> result = new HashMap<>();
        for (TableRule each : shardingRule.getTableRules()) {
            mergeDataSourceTableNameMap(result, toDataSourceTableNameMap(each));
        }
        return result;
    }
    
    private static Map<String, Map<String, String>> toDataSourceTableNameMap(final TableRule tableRule) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Entry<String, Collection<String>> entry : tableRule.getDatasourceToTablesMap().entrySet()) {
            Map<String, String> tableNameMap = result.get(entry.getKey());
            if (null == tableNameMap) {
                result.put(entry.getKey(), toTableNameMap(tableRule.getLogicTable(), entry.getValue()));
            } else {
                tableNameMap.putAll(toTableNameMap(tableRule.getLogicTable(), entry.getValue()));
            }
        }
        return result;
    }
    
    private static Map<String, String> toTableNameMap(final String logicalTable, final Collection<String> actualTables) {
        Map<String, String> result = new HashMap<>();
        for (String each : actualTables) {
            result.put(each, logicalTable);
        }
        return result;
    }
    
    private static void mergeDataSourceTableNameMap(final Map<String, Map<String, String>> mergedResult, final Map<String, Map<String, String>> newDataSourceTableNameMap) {
        for (Entry<String, Map<String, String>> entry : newDataSourceTableNameMap.entrySet()) {
            Map<String, String> tableNameMap = mergedResult.get(entry.getKey());
            if (null == tableNameMap) {
                mergedResult.put(entry.getKey(), entry.getValue());
            } else {
                tableNameMap.putAll(entry.getValue());
            }
        }
    }
    
    private static DumperConfiguration createDumperConfiguration(final String dataSourceName, final DataSourceConfiguration dataSourceConfiguration, final Map<String, String> tableMap) {
        DumperConfiguration result = new DumperConfiguration();
        result.setDataSourceName(dataSourceName);
        Map<String, Object> dataSourceProperties = dataSourceConfiguration.getProps();
        JDBCDataSourceConfiguration dumperDataSourceConfiguration = new JDBCDataSourceConfiguration(
                dataSourceProperties.containsKey("jdbcUrl") ? dataSourceProperties.get("jdbcUrl").toString() : dataSourceProperties.get("url").toString(),
                dataSourceProperties.get("username").toString(), dataSourceProperties.get("password").toString());
        result.setDataSourceConfiguration(dumperDataSourceConfiguration);
        result.setTableNameMap(tableMap);
        return result;
    }
    
    private static ImporterConfiguration createImporterConfiguration(final ScalingConfiguration scalingConfiguration, final ShardingRuleConfiguration shardingRuleConfig) {
        ImporterConfiguration result = new ImporterConfiguration();
        result.setDataSourceConfiguration(scalingConfiguration.getRuleConfiguration().getTarget().toTypedDataSourceConfiguration());
        result.setShardingColumnsMap(toShardingColumnsMap(shardingRuleConfig));
        return result;
    }
    
    private static Map<String, Set<String>> toShardingColumnsMap(final ShardingRuleConfiguration shardingRuleConfig) {
        Map<String, Set<String>> result = Maps.newConcurrentMap();
        for (ShardingTableRuleConfiguration each : shardingRuleConfig.getTables()) {
            Set<String> shardingColumns = Sets.newHashSet();
            shardingColumns.addAll(extractShardingColumns(each.getDatabaseShardingStrategy()));
            shardingColumns.addAll(extractShardingColumns(each.getTableShardingStrategy()));
            result.put(each.getLogicTable(), shardingColumns);
        }
        return result;
    }
    
    private static Set<String> extractShardingColumns(final ShardingStrategyConfiguration shardingStrategy) {
        if (shardingStrategy instanceof StandardShardingStrategyConfiguration) {
            return Sets.newHashSet(((StandardShardingStrategyConfiguration) shardingStrategy).getShardingColumn());
        }
        if (shardingStrategy instanceof ComplexShardingStrategyConfiguration) {
            return Sets.newHashSet(((ComplexShardingStrategyConfiguration) shardingStrategy).getShardingColumns().split(","));
        }
        return Collections.emptySet();
    }
}
