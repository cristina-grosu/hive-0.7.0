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

package org.apache.hadoop.hive.ql.optimizer.ppr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.hive.ql.exec.ExprNodeEvaluator;
import org.apache.hadoop.hive.ql.exec.ExprNodeEvaluatorFactory;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

public class PartExprEvalUtils {
  /**
   * Evaluate expression with partition columns
   *
   * @param expr
   * @param partSpec
   * @param rowObjectInspector
   * @return value returned by the expression
   * @throws HiveException
   */
  static public Object evalExprWithPart(ExprNodeDesc expr, LinkedHashMap<String, String> partSpec,
      StructObjectInspector rowObjectInspector) throws HiveException {
    Object[] rowWithPart = new Object[2];
    // Create the row object
    ArrayList<String> partNames = new ArrayList<String>();
    ArrayList<String> partValues = new ArrayList<String>();
    ArrayList<ObjectInspector> partObjectInspectors = new ArrayList<ObjectInspector>();
    for (Map.Entry<String, String> entry : partSpec.entrySet()) {
      partNames.add(entry.getKey());
      partValues.add(entry.getValue());
      partObjectInspectors
          .add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
    }
    StructObjectInspector partObjectInspector = ObjectInspectorFactory
        .getStandardStructObjectInspector(partNames, partObjectInspectors);

    rowWithPart[1] = partValues;
    ArrayList<StructObjectInspector> ois = new ArrayList<StructObjectInspector>(
        2);
    ois.add(rowObjectInspector);
    ois.add(partObjectInspector);
    StructObjectInspector rowWithPartObjectInspector = ObjectInspectorFactory
        .getUnionStructObjectInspector(ois);

    ExprNodeEvaluator evaluator = ExprNodeEvaluatorFactory
        .get(expr);
    ObjectInspector evaluateResultOI = evaluator
        .initialize(rowWithPartObjectInspector);
    Object evaluateResultO = evaluator.evaluate(rowWithPart);

    return ((PrimitiveObjectInspector) evaluateResultOI)
        .getPrimitiveJavaObject(evaluateResultO);
  }
}
