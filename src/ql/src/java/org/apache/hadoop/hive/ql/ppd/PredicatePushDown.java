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

package org.apache.hadoop.hive.ql.ppd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.hive.ql.exec.Operator;
import org.apache.hadoop.hive.ql.lib.DefaultGraphWalker;
import org.apache.hadoop.hive.ql.lib.DefaultRuleDispatcher;
import org.apache.hadoop.hive.ql.lib.Dispatcher;
import org.apache.hadoop.hive.ql.lib.GraphWalker;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.lib.NodeProcessor;
import org.apache.hadoop.hive.ql.lib.Rule;
import org.apache.hadoop.hive.ql.lib.RuleRegExp;
import org.apache.hadoop.hive.ql.optimizer.Transform;
import org.apache.hadoop.hive.ql.parse.OpParseContext;
import org.apache.hadoop.hive.ql.parse.ParseContext;
import org.apache.hadoop.hive.ql.parse.SemanticException;

/**
 * Implements predicate pushdown. Predicate pushdown is a term borrowed from
 * relational databases even though for Hive it is predicate pushup. The basic
 * idea is to process expressions as early in the plan as possible. The default
 * plan generation adds filters where they are seen but in some instances some
 * of the filter expressions can be pushed nearer to the operator that sees this
 * particular data for the first time. e.g. select a.*, b.* from a join b on
 * (a.col1 = b.col1) where a.col1 > 20 and b.col2 > 40
 *
 * For the above query, the predicates (a.col1 > 20) and (b.col2 > 40), without
 * predicate pushdown, would be evaluated after the join processing has been
 * done. Suppose the two predicates filter out most of the rows from a and b,
 * the join is unnecessarily processing these rows. With predicate pushdown,
 * these two predicates will be processed before the join.
 *
 * Predicate pushdown is enabled by setting hive.optimize.ppd to true. It is
 * disable by default.
 *
 * The high-level algorithm is describe here - An operator is processed after
 * all its children have been processed - An operator processes its own
 * predicates and then merges (conjunction) with the processed predicates of its
 * children. In case of multiple children, there are combined using disjunction
 * (OR). - A predicate expression is processed for an operator using the
 * following steps - If the expr is a constant then it is a candidate for
 * predicate pushdown - If the expr is a col reference then it is a candidate
 * and its alias is noted - If the expr is an index and both the array and index
 * expr are treated as children - If the all child expr are candidates for
 * pushdown and all of the expression reference only one alias from the
 * operator's RowResolver then the current expression is also a candidate One
 * key thing to note is that some operators (Select, ReduceSink, GroupBy, Join
 * etc) change the columns as data flows through them. In such cases the column
 * references are replaced by the corresponding expression in the input data.
 */
public class PredicatePushDown implements Transform {

  private ParseContext pGraphContext;
  private HashMap<Operator<? extends Serializable>, OpParseContext> opToParseCtxMap;

  @Override
  public ParseContext transform(ParseContext pctx) throws SemanticException {
    pGraphContext = pctx;
    opToParseCtxMap = pGraphContext.getOpParseCtx();

    // create a the context for walking operators
    OpWalkerInfo opWalkerInfo = new OpWalkerInfo(pGraphContext);

    Map<Rule, NodeProcessor> opRules = new LinkedHashMap<Rule, NodeProcessor>();
    opRules.put(new RuleRegExp("R1", "FIL%"), OpProcFactory.getFilterProc());
    opRules.put(new RuleRegExp("R3", "JOIN%"), OpProcFactory.getJoinProc());
    opRules.put(new RuleRegExp("R4", "RS%"), OpProcFactory.getRSProc());
    opRules.put(new RuleRegExp("R5", "TS%"), OpProcFactory.getTSProc());
    opRules.put(new RuleRegExp("R6", "SCR%"), OpProcFactory.getSCRProc());
    opRules.put(new RuleRegExp("R6", "LIM%"), OpProcFactory.getLIMProc());
    opRules.put(new RuleRegExp("R7", "UDTF%"), OpProcFactory.getUDTFProc());
    opRules.put(new RuleRegExp("R8", "LVF%"), OpProcFactory.getLVFProc());

    // The dispatcher fires the processor corresponding to the closest matching
    // rule and passes the context along
    Dispatcher disp = new DefaultRuleDispatcher(OpProcFactory.getDefaultProc(),
        opRules, opWalkerInfo);
    GraphWalker ogw = new DefaultGraphWalker(disp);

    // Create a list of topop nodes
    ArrayList<Node> topNodes = new ArrayList<Node>();
    topNodes.addAll(pGraphContext.getTopOps().values());
    ogw.startWalking(topNodes, null);

    return pGraphContext;
  }

}
