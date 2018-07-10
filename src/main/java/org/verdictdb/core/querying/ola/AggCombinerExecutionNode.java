package org.verdictdb.core.querying.ola;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.core.connection.DbmsQueryResult;
import org.verdictdb.core.execution.ExecutionInfoToken;
import org.verdictdb.core.querying.CreateTableAsSelectNode;
import org.verdictdb.core.querying.ExecutableNodeBase;
import org.verdictdb.core.querying.IdCreator;
import org.verdictdb.core.querying.QueryNodeBase;
import org.verdictdb.core.querying.SubscriptionTicket;
import org.verdictdb.core.sqlobject.*;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.exception.VerdictDBValueException;

public class AggCombinerExecutionNode extends CreateTableAsSelectNode {

  AggMeta aggMeta = new AggMeta();

  private AggCombinerExecutionNode(IdCreator namer) {
    super(namer, null);
  }
  
  public static AggCombinerExecutionNode create(
      IdCreator namer,
      ExecutableNodeBase leftQueryExecutionNode,
      ExecutableNodeBase rightQueryExecutionNode) throws VerdictDBValueException {
    AggCombinerExecutionNode node = new AggCombinerExecutionNode(namer);
    
    SelectQuery rightQuery = ((QueryNodeBase) rightQueryExecutionNode).getSelectQuery();   // the right one is the aggregate query
    String leftAliasName = namer.generateAliasName();
    String rightAliasName = namer.generateAliasName();
    
    // create placeholders to use
    Pair<BaseTable, SubscriptionTicket> leftBaseAndTicket = node.createPlaceHolderTable(leftAliasName);
    Pair<BaseTable, SubscriptionTicket> rightBaseAndTicket = node.createPlaceHolderTable(rightAliasName);
    
    // compose a join query
    SelectQuery joinQuery = composeJoinQuery(rightQuery, leftBaseAndTicket.getLeft(), rightBaseAndTicket.getLeft());
    
    leftQueryExecutionNode.registerSubscriber(leftBaseAndTicket.getRight());
    rightQueryExecutionNode.registerSubscriber(rightBaseAndTicket.getRight());
    
    node.setSelectQuery(joinQuery);
    return node;
  }
  
  /**
   * Composes a query that joins two tables. The select list is inferred from a given query.
   * 
   * @param rightQuery The query from which to infer a select list
   * @param leftBase
   * @param rightBase
   * @return
   */
  static SelectQuery composeJoinQuery(
      SelectQuery rightQuery,
      BaseTable leftBase,
      BaseTable rightBase) {
    
    // retrieves the select list
    List<String> groupAliasNames = new ArrayList<>();
    List<String> measureAliasNames = new ArrayList<>();
    HashMap<String, String> maxminAliasNames = new HashMap<>();
    for (SelectItem item : rightQuery.getSelectList()) {
      if (item.isAggregateColumn()) {
        if (item instanceof AliasedColumn && ((AliasedColumn) item).getColumn() instanceof ColumnOp
            && (((ColumnOp) ((AliasedColumn) item).getColumn()).getOpType().equals("max")
            || ((ColumnOp) ((AliasedColumn) item).getColumn()).getOpType().equals("min"))) {
          maxminAliasNames.put(((AliasedColumn) item).getAliasName(), ((ColumnOp) ((AliasedColumn) item).getColumn()).getOpType());
        }
        measureAliasNames.add(((AliasedColumn) item).getAliasName());
      } else {
        groupAliasNames.add(((AliasedColumn) item).getAliasName());
      }
    }
    
    // replace the alias names of those select items
    String leftAliasName = leftBase.getAliasName().get();
    String rightAliasName = rightBase.getAliasName().get();
    
    List<SelectItem> groupItems = new ArrayList<>();
    List<SelectItem> measureItems = new ArrayList<>();
    for (String a : groupAliasNames) {
      groupItems.add(new AliasedColumn(new BaseColumn(leftAliasName, a), a));
    }
    for (String a : measureAliasNames) {
      if (maxminAliasNames.keySet().contains(a)) {
        if (maxminAliasNames.get(a).equals("max")) {
          measureItems.add(new AliasedColumn(
              new ColumnOp("whenthenelse", Arrays.asList(
                  new ColumnOp("greater", Arrays.<UnnamedColumn>asList(new BaseColumn(leftAliasName, a), new BaseColumn(rightAliasName, a))),
                  new BaseColumn(leftAliasName, a),
                  new BaseColumn(rightAliasName, a)
              )),
              a));
        } else {
          measureItems.add(new AliasedColumn(
              new ColumnOp("whenthenelse", Arrays.asList(
                  new ColumnOp("less", Arrays.<UnnamedColumn>asList(new BaseColumn(leftAliasName, a), new BaseColumn(rightAliasName, a))),
                  new BaseColumn(leftAliasName, a),
                  new BaseColumn(rightAliasName, a)
              )),
              a));
        }
      }
      else measureItems.add(new AliasedColumn(
          ColumnOp.add(new BaseColumn(leftAliasName, a), new BaseColumn(rightAliasName, a)),
          a));
    }
    List<SelectItem> allItems = new ArrayList<>();
    allItems.addAll(groupItems);
    allItems.addAll(measureItems);
    
    // finally, creates a join query
    SelectQuery joinQuery = SelectQuery.create(
        allItems, 
        Arrays.<AbstractRelation>asList(leftBase, rightBase));
    for (String a : groupAliasNames) {
      joinQuery.addFilterByAnd(
          ColumnOp.equal(new BaseColumn(leftAliasName, a), new BaseColumn(rightAliasName, a)));
    }
    
    return joinQuery;
  }

  @Override
  public SqlConvertible createQuery(List<ExecutionInfoToken> tokens) throws VerdictDBException {
    for (ExecutionInfoToken token:tokens) {
      AggMeta aggMeta = (AggMeta) token.getValue("aggMeta");
      if (aggMeta!=null) {
        this.aggMeta.getCubes().addAll(aggMeta.getCubes());
        this.aggMeta.setAggAlias(aggMeta.getAggAlias());
        this.aggMeta.setOriginalSelectList(aggMeta.getOriginalSelectList());
        this.aggMeta.setAggColumn(aggMeta.getAggColumn());
        this.aggMeta.setAggColumnAggAliasPair(aggMeta.getAggColumnAggAliasPair());
        this.aggMeta.setAggColumnAggAliasPairOfMaxMin(aggMeta.getAggColumnAggAliasPairOfMaxMin());
        this.aggMeta.setMaxminAggAlias(aggMeta.getMaxminAggAlias());
      }
    }
    return super.createQuery(tokens);
  }

  @Override
  public ExecutionInfoToken createToken(DbmsQueryResult result) {
    ExecutionInfoToken token = super.createToken(result);
    token.setKeyValue("aggMeta", aggMeta);
    token.setKeyValue("dependentQuery", this.selectQuery);
    return token;
  }

}
