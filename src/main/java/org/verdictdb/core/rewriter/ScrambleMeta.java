package org.verdictdb.core.rewriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScrambleMeta {
    
    Map<String, ScrambleMetaForTable> meta = new HashMap<>();
    
    public ScrambleMeta() {}
    
    public void insertScrumbleMetaEntry(
            String schemaName,
            String tableName,
            String partitionColumn,
            String inclusionProbabilityColumn,
            String inclusionProbBlockDiffColumn,
            String subsampleColumn,
            int aggregationBlockCount) {
        ScrambleMetaForTable tableMeta = new ScrambleMetaForTable();
        tableMeta.setSchemaName(schemaName);
        tableMeta.setTableName(tableName);
        tableMeta.setPartitionColumn(partitionColumn);
        tableMeta.setSubsampleColumn(subsampleColumn);
        tableMeta.setInclusionProbabilityColumn(inclusionProbabilityColumn);
        tableMeta.setInclusionProbabilityBlockDifferenceColumn(inclusionProbBlockDiffColumn);
        tableMeta.setAggregationBlockCount(aggregationBlockCount);
        meta.put(metaKey(schemaName, tableName), tableMeta);
    }
    
    private String metaKey(String schemaName, String tableName) {
        return schemaName + "#" + tableName;
    }
    
    public String getPartitionColumn(String schemaName, String tableName) {
        return meta.get(metaKey(schemaName, tableName)).getPartitionColumn();
    }
    
    public int getAggregationBlockCount(String schemaName, String tableName) {
        return meta.get(metaKey(schemaName, tableName)).getAggregationBlockCount();
    }
    
    public String getInclusionProbabilityColumn(String schemaName, String tableName) {
        return meta.get(metaKey(schemaName, tableName)).getInclusionProbabilityColumn();
    }

    public String getSubsampleColumn(String schemaName, String tableName) {
        return meta.get(metaKey(schemaName, tableName)).getSubsampleColumn();
    }

    public String getInclusionProbabilityBlockDifferenceColumn(String schemaName, String tableName) {
        return meta.get(metaKey(schemaName, tableName)).getInclusionProbabilityBlockDifferenceColumn();
    }

}


/**
 * Table-specific information
 * @author Yongjoo Park
 *
 */
class ScrambleMetaForTable {
    
    String schemaName;
    
    String tableName;
    
    String partitionColumn;
    
    String inclusionProbabilityColumn;
    
    String inclusionProbabilityBlockDifferenceColumn;
    
    String subsampleColumn;
    
    int aggregationBlockCount;
    
    
    public ScrambleMetaForTable() {}
    
    public String getInclusionProbabilityBlockDifferenceColumn() {
        return inclusionProbabilityBlockDifferenceColumn;
    }

    public void setAggregationBlockCount(int aggregationBlockCount) {
        this.aggregationBlockCount = aggregationBlockCount;
    }
    
    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public int getAggregationBlockCount() {
        return aggregationBlockCount;
    }

    public String getPartitionColumn() {
        return partitionColumn;
    }

    public void setPartitionColumn(String partitionColumn) {
        this.partitionColumn = partitionColumn;
    }

    public String getInclusionProbabilityColumn() {
        return inclusionProbabilityColumn;
    }

    public void setInclusionProbabilityColumn(String inclusionProbabilityColumn) {
        this.inclusionProbabilityColumn = inclusionProbabilityColumn;
    }
    
    public void setInclusionProbabilityBlockDifferenceColumn(String inclusionProbabilityBlockDifferenceColumn) {
        this.inclusionProbabilityBlockDifferenceColumn = inclusionProbabilityBlockDifferenceColumn;
    }
    
    public void setSubsampleColumn(String subsampleColumn) {
        this.subsampleColumn = subsampleColumn;
    }

    public String getSubsampleColumn() {
        return subsampleColumn;
    }
}
