package org.example.graspdb.CypherTransform;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.example.graspdb.Randomly;
import org.example.graspdb.cypher.CypherGlobalState;
import org.example.graspdb.cypher.schema.CypherSchema;
import org.example.graspdb.parsercypher.CypherLexer;
import org.example.graspdb.parsercypher.CypherParser;
import org.example.graspdb.parsercypher.CypherParserBaseVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.example.graspdb.parserutil.parserUtils.getFullTextOriginal;

public class CypherVisitorImpNoUpdate<G extends CypherGlobalState<?, S>, S extends CypherSchema<G, ?>> extends CypherParserBaseVisitor<String> {
    private String result_qurey;
    private G globalState;
    private List<String> list_function=Arrays.asList("nodes","toIntegerList","toStringList","toBooleanList","range","relationships","keys","labels","collect");
    private List<String> string_function=Arrays.asList("type","substring","toStringOrNull");
    private List<String> boolean_function=Arrays.asList("exists","isEmpty","toBooleanOrNull");
    private List<String> with_operator = Arrays.asList("WITH * ","WITH 1 as w ","WITH NULL as w ");
    private List<String> where_operator = Arrays.asList("WHERE TRUE ");
    private List<String> order_operator = Arrays.asList("ORDER BY TRUE ","ORDER BY FALSE ","ORDER BY NULL ","ORDER BY 1 ");
    private List<String> order_operator_add = Arrays.asList(",TRUE ",",FALSE ",",NULL ",",1 ");
    private List<String> unwind_operator_mem = Arrays.asList("UNWIND [1] as l","UNWIND [NULL] as l");
    private List<String> unwind_operator = Arrays.asList("UNWIND [1] as l","UNWIND [NULL] as l","UNWIND 1 as l");
    private List<String> case_operator = Arrays.asList("CASE WHEN TRUE THEN ","CASE WHEN FALSE THEN NULL ELSE ","END ");
    private List<String> reduce_operator = Arrays.asList("reduce(a=",",b IN []|a) ");
    private List<String> list_add_operator = Arrays.asList("([]+","+[]) ");
    private List<String> string_add_operator = Arrays.asList("(\'\'+","+\'\') ");
    private List<String> bool_add_operator = Arrays.asList("TRUE AND ","FALSE OR ","AND TRUE ","OR FALSE ");
    private List<String> return_operator = Arrays.asList("RETURN * ","RETURN NULL ");
    private List<String> path_operator = Arrays.asList("path","=");
    private List<String> skip_operator = Arrays.asList("SKIP 0 ");
    private List<String> limit_operator = Arrays.asList("LIMIT 2147483647 ");
    public CypherVisitorImpNoUpdate(){
        this.result_qurey="";
        this.r=new Randomly();
        this.ID=0;
        this.create_id=0;
        this.unwind_id=0;
        this.path_id=0;
    }
    public void setGlobalState(G g){
        this.globalState=g;
    }
    public int ID=0;
    public int create_id=0;
    public int unwind_id=0;
    public int path_id=0;
    public Randomly r;
    public static Boolean in_subquery=false;
    public String Getresult_qurey(){
        return this.result_qurey;
    }
    public void Clear(){
        this.result_qurey="";
        this.ID=0;
        this.create_id=0;
        this.unwind_id=0;
        this.path_id=0;
    }
    @Override
    public String visitTerminal(TerminalNode node) {
        if(!node.getText().equals(";")&&!node.getText().equals("<EOF>"))
            result_qurey+=node.getText()+" ";
        return null;
    }
    @Override
    public String visitPatternWhere(CypherParser.PatternWhereContext ctx) {
        //todo 对于非Redis数据库，只拆分最外层的matchstatement语句中的模式（忽略子查询中的match模式）
        if (ctx.parent.parent.parent instanceof CypherParser.SingleQueryContext && r.getInteger(0,6)==0 && !globalState.getDatabaseName().contains("redis"))
        {
            List<String> identifiers = new ArrayList<>();
            List<String> patterns = new ArrayList<>();
            List<String> single_node_patterns = new ArrayList<>();
            int random = r.getInteger(0, ctx.pattern().patternPart().size());
            for (int i = 0; i < ctx.pattern().patternPart().size(); i++) {
                CypherParser.PatternPartContext p = ctx.pattern().patternPart().get(i);
                //todo 随机选择一个模式拆分
                if (i == random) {
                    if (p.ASSIGN() != null) {
                        identifiers.add(getFullTextOriginal(p));
                    } else {
                        CypherParser.PatternElemContext pe = p.patternElem();
                        while (pe.patternElem() != null)
                            pe = pe.patternElem();
                        //对于只有一个点的模式，只提取属性值
                        if (pe.patternElemChain().size() == 0) {
                            if (pe.nodePattern().properties() == null || pe.nodePattern().symbol() == null) {
                                identifiers.add(getFullTextOriginal(pe));
                            } else {
                                if (pe.nodePattern().nodeLabels() != null)
                                    identifiers.add("(" + pe.nodePattern().symbol().getText() + pe.nodePattern().nodeLabels().getText() + ")");
                                else
                                    identifiers.add("(" + pe.nodePattern().symbol().getText() + ")");
                                for (CypherParser.MapPairContext m : pe.nodePattern().properties().mapLit().mapPair()) {
                                    single_node_patterns.add(pe.nodePattern().symbol().getText() + "." + m.name().getText() + "=" + getFullTextOriginal(m.expression()));
                                }
                            }
                        }
                        //对于有边的模式
                        else {
                            String pattern = "";
                            CypherParser.NodePatternContext np = pe.nodePattern();
                            if (np.symbol() != null && np.nodeLabels() != null) {
                                identifiers.add("(" + np.symbol().getText() + np.nodeLabels().getText() + ")");
                            } else if (np.symbol() != null) {
                                identifiers.add("(" + np.symbol().getText() + ")");
                            }
                            else if(np.nodeLabels() != null)
                            {identifiers.add("(" + "nn" +this.ID+ np.nodeLabels().getText() +")");
                                ID++;
                            }
                            else
                            {identifiers.add("(" + "nn" +this.ID+ ")");
                                ID++;
                            }
                            pattern += "(";
                            if (np.symbol() != null)
                                pattern += np.symbol().getText();
                            else
                            {
                                ID--;
                                pattern += "nn"+this.ID;
                                ID++;
                            }
                            if (np.properties() != null)
                                pattern += getFullTextOriginal(np.properties());
                            pattern += ")";
                            for (CypherParser.PatternElemChainContext pec : pe.patternElemChain()) {
                                np = pec.nodePattern();
                                CypherParser.RelationshipPatternContext rp = pec.relationshipPattern();
                                String id = "";
                                if (rp.relationDetail().symbol() != null) {
                                    id = rp.relationDetail().symbol().getText();
                                } else {
                                    id = "m" + this.ID;
                                    this.ID++;
                                }
                                String lr_id = "";
                                String rr_id = "";
                                String lr_p = "";
                                String rr_p = "";
                                if (rp.LT() != null) {
                                    lr_id = "<-";
                                    lr_p = "<-";
                                    rr_id = "-";
                                    rr_p = "-";
                                } else if (rp.GT() != null) {
                                    lr_id = "-";
                                    lr_p = "-";
                                    rr_id = "->";
                                    rr_p = "->";
                                }
                                //todo 模式拆分后的关系必须有方向
                                else {
                                    lr_id = "<-";
                                    rr_id = "-";
                                    lr_p = "-";
                                    rr_p = "-";
                                }
                                String r = "()" + lr_id + "[" + id;
                                if (rp.relationDetail().relationshipTypes() != null) {
                                    r += rp.relationDetail().relationshipTypes().getText();
                                }
                                if (rp.relationDetail().rangeLit() != null) {
                                    r += rp.relationDetail().rangeLit().getText();
                                }
                                if (rp.relationDetail().properties() != null) {
                                    r += getFullTextOriginal(rp.relationDetail().properties());
                                }
                                identifiers.add(r + "]" + rr_id + "()");
                                pattern += lr_p + "[" + id + "]" + rr_p;
                                if (np.symbol() != null && np.nodeLabels() != null) {
                                    identifiers.add("(" + np.symbol().getText() + np.nodeLabels().getText() + ")");
                                } else if (np.symbol() != null) {
                                    identifiers.add("(" + np.symbol().getText() + ")");
                                }
                                else if(np.nodeLabels() != null)
                                {
                                    identifiers.add("(" + "nn"+ this.ID + np.nodeLabels().getText() + ")");
                                    ID++;
                                }
                                else
                                {identifiers.add("(" + "nn" +this.ID+ ")");
                                    ID++;
                                }
                                pattern += "(";
                                if (np.symbol() != null)
                                    pattern += np.symbol().getText();
                                else
                                {
                                    ID--;
                                    pattern += "nn"+this.ID;
                                    ID++;
                                }
                                if (np.properties() != null)
                                    pattern += getFullTextOriginal(np.properties());
                                pattern += ")";
                            }
                            patterns.add("(" + pattern + ")");
                        }
                    }
                }
                //todo 不拆分
                else {
                    identifiers.add(getFullTextOriginal(p));
                }
            }
            if (identifiers.size() == 0)
                result_qurey += "() ";
            else {
                for (String i : identifiers)
                    result_qurey += i + ",";
                result_qurey = result_qurey.substring(0, result_qurey.length() - 1) + " ";
            }
            //todo 延迟过滤策略只能应用于MATCH而不能用于OPTIONAL MATCH，详情见OPTIONAL MATCH文档
            if(r.getInteger(0,9)==1&&((CypherParser.MatchStContext) ctx.parent).OPTIONAL()==null)
                result_qurey+=with_operator.get(0);
            if (patterns.size() != 0 || single_node_patterns.size() != 0) {
                result_qurey += "WHERE ";
                for (String p : patterns)
                    result_qurey += "exists" + p + " AND ";
                for(String s : single_node_patterns)
                    result_qurey += s + " AND ";
                if (ctx.where() != null) {
                    result_qurey += "(";
                    visitChildren(ctx.where().expression());
                    result_qurey += ")";
                } else {
                    result_qurey = result_qurey.substring(0, result_qurey.length() - 4);
                }
            } else if(ctx.where()!=null) {
                visitChildren(ctx.where());
            }
            else if(r.getInteger(0,9)==0)
                result_qurey+=where_operator.get(0);
        }
        //不进行模式拆分
        else{
            visitChildren(ctx.pattern());
            //todo 延迟过滤策略
            if(r.getInteger(0,9)==1&&((CypherParser.MatchStContext) ctx.parent).OPTIONAL()==null)
                result_qurey+=with_operator.get(0);
            if(ctx.where()!=null)
                visitChildren(ctx.where());
                //变异where true
            else if(r.getInteger(0,9)==0){
                result_qurey+=where_operator.get(0);
            }
        }
        return null;
    }
    @Override
    public String visitPatternPart(CypherParser.PatternPartContext ctx) {
        //进行path ID变异
        if(r.getInteger(0,9)==0&&ctx.symbol()==null)
        {result_qurey+=path_operator.get(0)+path_id+path_operator.get(1);
            path_id++;}
        return visitChildren(ctx);
    }
    @Override
    public String visitMatchSt(CypherParser.MatchStContext ctx) {
        visitChildren(ctx);
        return null;
    }
    @Override
    public String visitDeleteSt(CypherParser.DeleteStContext ctx) {
        visitChildren(ctx);
        return null;
    }
    @Override
    public String visitUnwindSt(CypherParser.UnwindStContext ctx) {
        visitChildren(ctx);
        return null;
    }
    @Override
    public String visitQueryCallSt(CypherParser.QueryCallStContext ctx) {
        visitChildren(ctx);
        return null;
    }
    @Override
    public String visitUnionSt(CypherParser.UnionStContext ctx) {
        return visitChildren(ctx);
    }
    @Override
    public String visitReturnSt(CypherParser.ReturnStContext ctx) {
        return visitChildren(ctx);
    }
    @Override
    public String visitReadingStatement(CypherParser.ReadingStatementContext ctx) {
        Boolean next_reading = false;
        //标识下一个子句是否是unwind.match,call
        if(ctx.parent instanceof CypherParser.SingleQueryContext){
            List<ParseTree> l=((CypherParser.SingleQueryContext) ctx.parent).children;
            int size=l.size();
            for(int i=0;i<size-1;i++){
                if(l.get(i).equals(ctx)&&l.get(i+1) instanceof CypherParser.ReadingStatementContext)
                {next_reading=true;
                    break;}
            }
        }
        visitChildren(ctx);
        //with mutation
        if(r.getInteger(0,6)==0&&!(ctx.parent instanceof CypherParser.ForeachStContext)){
            result_qurey+=with_operator.get(0);
        }
        //unwind
        else if(r.getInteger(0,6)==1&&!(ctx.parent instanceof CypherParser.ForeachStContext)){
            if(globalState.getDatabaseName().contains("memgraph"))
                result_qurey+=Randomly.fromList(unwind_operator_mem)+unwind_id+" ";
            else
                result_qurey+=Randomly.fromList(unwind_operator)+unwind_id+" ";
            unwind_id++;
        }
   result_qurey+=with_operator.get(0);
//        }
        return null;
    }
    @Override
    public String visitUpdatingStatement(CypherParser.UpdatingStatementContext ctx) {
        visitChildren(ctx);
        //with mutation
        if(r.getInteger(0,9)==0&&!(ctx.parent instanceof CypherParser.ForeachStContext)){
            result_qurey+=with_operator.get(0);
        }
        //create mutation
//        else if(r.getInteger(0,9)==0&&!in_subquery){
//            result_qurey+=create_operator.get(0)+create_id+create_operator.get(1)+create_id+create_operator.get(2);
//            create_id++;
//        }
        //进行delete变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis"))
//            result_qurey+=Randomly.fromList(delete_operator);
            //进行remove变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis"))
//            result_qurey+=Randomly.fromList(remove_operator);
            //进行set变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis"))
//            result_qurey+=Randomly.fromList(set_operator);
        return null;
    }
    @Override
    public String visitForeachSt(CypherParser.ForeachStContext ctx) {
        visitChildren(ctx);
        return null;
    }
    @Override
    public String visitWithSt(CypherParser.WithStContext ctx) {
        Boolean next_reading = false;
        //标识下一个子句是否是match,unwind,call
        if(ctx.parent instanceof CypherParser.SingleQueryContext)
        {
            List<ParseTree> l=((CypherParser.SingleQueryContext) ctx.parent).children;
            int size=l.size();
            for(int i=0;i<size-1;i++){
                if(l.get(i).equals(ctx)&&l.get(i+1) instanceof CypherParser.ReadingStatementContext)
                {next_reading=true;
                    break;}
            }
        }
        result_qurey+="WITH ";
        visitChildren(ctx.projectionBody());
        if(ctx.where()!=null)
            visitChildren(ctx.where());
        else{
            if(r.getInteger(0,9)==0&&!(ctx.parent.parent instanceof CypherParser.QueryCallStContext))
                result_qurey+=where_operator.get(0);
        }
        if(r.getInteger(0,9)==0&&!(ctx.parent instanceof CypherParser.ForeachStContext)){
            result_qurey+=with_operator.get(0);
        }
        else if(r.getInteger(0,9)==0&&!(ctx.parent instanceof CypherParser.ForeachStContext)){
            if(globalState.getDatabaseName().contains("memgraph"))
                result_qurey+=Randomly.fromList(unwind_operator_mem)+unwind_id+" ";
            else
                result_qurey+=Randomly.fromList(unwind_operator)+unwind_id+" ";
            unwind_id++;
        }
        //进行create变异
//        else if(r.getInteger(0,9)==0&&!in_subquery){
//            result_qurey+=create_operator.get(0)+create_id+create_operator.get(1)+create_id+create_operator.get(2);
//            create_id++;
//            if(next_reading)
//                result_qurey+=with_operator.get(0);
//        }
        //进行delete变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis")){
//            result_qurey+=Randomly.fromList(delete_operator);
//            if(next_reading)
//                result_qurey+=with_operator.get(0);
//        }
        //进行remove变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis")){
//            result_qurey+=Randomly.fromList(remove_operator);
//            if(next_reading)
//                result_qurey+=with_operator.get(0);
//        }
        //进行set变异
//        else if(r.getInteger(0,9)==0&&!in_subquery&&!globalState.getDatabaseName().contains("redis")){
//            result_qurey+=Randomly.fromList(set_operator);
//            if(next_reading)
//                result_qurey+=with_operator.get(0);
//        }
        return null;
    }
    @Override public String visitWhere(CypherParser.WhereContext ctx) {
        return visitChildren(ctx);
    }
    @Override public String visitProjectionBody(CypherParser.ProjectionBodyContext ctx) {
        if(ctx.DISTINCT()!=null)
            result_qurey+="DISTINCT ";
        visitChildren(ctx.projectionItems());
        //order by mutation
        if(ctx.orderSt()==null&&r.getInteger(0,9)==0)
            result_qurey+=Randomly.fromList(order_operator);
        if(ctx.orderSt()!=null)
        {result_qurey+=getFullTextOriginal(ctx.orderSt())+" ";
            if(r.getInteger(0,9)==0)
                result_qurey+=Randomly.fromList(order_operator_add);}
        //skip mutation
        if(ctx.skipSt()!=null)
            visitChildren(ctx.skipSt());
        else if(r.getInteger(0,9)==0)
            result_qurey+=Randomly.fromList(skip_operator);
        //limit mutation
        if(ctx.limitSt()!=null)
            visitChildren(ctx.limitSt());
        else if(r.getInteger(0,9)==0)
            result_qurey+=Randomly.fromList(limit_operator);
        return null;
    }
    @Override
    public String visitExpression(CypherParser.ExpressionContext ctx) {
        //排除exists()函数的参数
        if(ctx.parent.parent instanceof CypherParser.FunctionInvocationContext && ((CypherParser.FunctionInvocationContext) ctx.parent.parent).functionname().EXISTSF()!=null)
            return visitChildren(ctx);
            //case when变异
        else if(r.getInteger(0,9)==0){
            result_qurey+=Randomly.fromList(case_operator.subList(0,2));
            visitChildren(ctx);
            result_qurey+=case_operator.get(2);
            //对于with ID 情况的特殊处理
            if(ctx.parent.parent.parent.parent instanceof CypherParser.WithStContext && ctx.parent instanceof CypherParser.ProjectionItemContext && ((CypherParser.ProjectionItemContext)ctx.parent).AS()==null)
            {
                result_qurey+="AS "+ctx.getText()+" ";
            }
            return null;
        }
        //reduce变异
        else if (r.getInteger(0,9)==0) {
            result_qurey+=reduce_operator.get(0);
            visitChildren(ctx);
            result_qurey+=reduce_operator.get(1);
            //对于with ID 情况的特殊处理
            if(ctx.parent.parent.parent.parent instanceof CypherParser.WithStContext && ctx.parent instanceof CypherParser.ProjectionItemContext && ((CypherParser.ProjectionItemContext)ctx.parent).AS()==null)
            {
                result_qurey+="AS "+ctx.getText()+" ";
            }
            return null;
        }
        else
            return visitChildren(ctx);
    }
    //list add、string add、boolean add变异
    @Override
    public String visitFunctionInvocation(CypherParser.FunctionInvocationContext ctx) {
        if(list_function.contains(ctx.functionname().getText())&&r.getInteger(0,9)==0){
            Boolean b=Randomly.getBoolean();
            if(b)
            {result_qurey+=list_add_operator.get(0);
                visitChildren(ctx);
                result_qurey+=")";
            }
            if(!b){
                result_qurey+="(";
                visitChildren(ctx);
                result_qurey+=list_add_operator.get(1);}
            return null;
        }
        else if(string_function.contains(ctx.functionname().getText())&&r.getInteger(0,9)==0){
            Boolean b=Randomly.getBoolean();
            if(b){
                result_qurey+=string_add_operator.get(0);
                visitChildren(ctx);
                result_qurey+=")";
            }
            if(!b){
                result_qurey+="(";
                visitChildren(ctx);
                result_qurey+=string_add_operator.get(1);
            }
            return null;
        }
        else if(boolean_function.contains(ctx.functionname().getText())&&r.getInteger(0,9)==0){
            Boolean b=Randomly.getBoolean();
            if(b)
                result_qurey+=Randomly.fromList(bool_add_operator.subList(0,2));
            visitChildren(ctx);
            if(!b)
                result_qurey+=Randomly.fromList(bool_add_operator.subList(2,4));
            return null;
        }
        else
            return visitChildren(ctx) ;
    }
    //list add变异
    @Override
    public String visitListLit(CypherParser.ListLitContext ctx) {
        int b=r.getInteger(0,9);
        if(b==0)
        {result_qurey+=list_add_operator.get(0);
            visitChildren(ctx);
            result_qurey+=")";}
        else if(b==1)
        {
            result_qurey+="(";
            visitChildren(ctx);
            result_qurey+=list_add_operator.get(1);}
        else
            visitChildren(ctx);
        return null;
    }
    //string add变异
    @Override
    public String visitStringLit(CypherParser.StringLitContext ctx) {
        int b=r.getInteger(0,9);
        if(b==1){
            result_qurey+=string_add_operator.get(0);
            visitChildren(ctx);
            result_qurey+=")";
        }
        else if(b==0){
            result_qurey+="(";
            visitChildren(ctx);
            result_qurey+=string_add_operator.get(1);
        }
        else
            visitChildren(ctx);
        return null;
    }
    //boolean add变异
    @Override
    public String visitBoolLit(CypherParser.BoolLitContext ctx) {
        int b=r.getInteger(0,9);
        if(b==1){
            result_qurey+=Randomly.fromList(bool_add_operator.subList(0,2));
            visitChildren(ctx);}
        else if(b==0){
            visitChildren(ctx);
            result_qurey+=Randomly.fromList(bool_add_operator.subList(2,4));}
        else
            visitChildren(ctx);
        return null;
    }
    @Override
    public String visitSingleQuery(CypherParser.SingleQueryContext ctx) {
        visitChildren(ctx);
        //进行return 变异
        if(r.getInteger(0,9)==0&&ctx.returnSt()==null)
            result_qurey+=Randomly.fromList(return_operator);
        return null;
    }
    @Override
    public String visitSubqueryExist(CypherParser.SubqueryExistContext ctx) {
        //todo 进行子查询转化变异
        if(r.getInteger(0,6)==0)
        {
            result_qurey+="(COUNT"+getFullTextOriginal(ctx).substring(6)+">0) ";
            return null;
        }
        else{
            in_subquery=true;
            int b=r.getInteger(0,9);
            //进行boolean add 变异
            if(b==0)
                result_qurey+=Randomly.fromList(bool_add_operator.subList(0,2));
            for(int i=0;i<ctx.children.size()-1;i++)
                ctx.getChild(i).accept(this);
            //进行return 变异
            if(r.getInteger(0,9)==0&&ctx.returnSt()==null)
            {
                result_qurey+=Randomly.fromList(return_operator);}
            result_qurey+="}";
            if(b==1)
                result_qurey+=Randomly.fromList(bool_add_operator.subList(2,4));
            in_subquery=false;
            return null;}
    }
    @Override
    public String visitSubqueryCount(CypherParser.SubqueryCountContext ctx) {
        in_subquery=true;
        for(int i=0;i<ctx.children.size()-1;i++)
            ctx.getChild(i).accept(this);
        //进行return 变异
        if(r.getInteger(0,9)==0&&ctx.returnSt()==null)
        {
            result_qurey+=Randomly.fromList(return_operator);}
        result_qurey+="}";
        in_subquery=false;
        return null;
    }


    //todo 其他不需要重载的函数
//    @Override public String visitQuery(CypherParser.QueryContext ctx) {return visitChildren(ctx);}
//    @Override public String visitRegularQuery(CypherParser.RegularQueryContext ctx) { return visitChildren(ctx); }
//    @Override public String visitSkipSt(CypherParser.SkipStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitLimitSt(CypherParser.LimitStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitScript(CypherParser.ScriptContext ctx) {return visitChildren(ctx) ;}
//    @Override public String visitProjectionBody(CypherParser.ProjectionBodyContext ctx) { return visitChildren(ctx); }
//    @Override public String visitProjectionItems(CypherParser.ProjectionItemsContext ctx) { return visitChildren(ctx); }
//    @Override public String visitProjectionItem(CypherParser.ProjectionItemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitOrderItem(CypherParser.OrderItemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitOrderSt(CypherParser.OrderStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRemoveSt(CypherParser.RemoveStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRemoveItem(CypherParser.RemoveItemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitParenExpressionChain(CypherParser.ParenExpressionChainContext ctx) { return visitChildren(ctx); }
//    @Override public String visitYieldItems(CypherParser.YieldItemsContext ctx) { return visitChildren(ctx); }
//    @Override public String visitYieldItem(CypherParser.YieldItemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitMergeSt(CypherParser.MergeStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitMergeAction(CypherParser.MergeActionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitSetSt(CypherParser.SetStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitSetItem(CypherParser.SetItemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitNodeLabels(CypherParser.NodeLabelsContext ctx) { return visitChildren(ctx); }
//    @Override public String visitCreateSt(CypherParser.CreateStContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPattern(CypherParser.PatternContext ctx) { return visitChildren(ctx); }
//    @Override public String visitXorExpression(CypherParser.XorExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitAndExpression(CypherParser.AndExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitNotExpression(CypherParser.NotExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitComparisonExpression(CypherParser.ComparisonExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitComparisonSigns(CypherParser.ComparisonSignsContext ctx) { return visitChildren(ctx); }
//    @Override public String visitAddSubExpression(CypherParser.AddSubExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitMultDivExpression(CypherParser.MultDivExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPowerExpression(CypherParser.PowerExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitUnaryAddSubExpression(CypherParser.UnaryAddSubExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitAtomicExpression(CypherParser.AtomicExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitListExpression(CypherParser.ListExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitStringExpression(CypherParser.StringExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitStringExpPrefix(CypherParser.StringExpPrefixContext ctx) { return visitChildren(ctx); }
//    @Override public String visitNullExpression(CypherParser.NullExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPropertyOrLabelExpression(CypherParser.PropertyOrLabelExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPropertyExpression(CypherParser.PropertyExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPatternElem(CypherParser.PatternElemContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPatternElemChain(CypherParser.PatternElemChainContext ctx) { return visitChildren(ctx); }
//    @Override public String visitProperties(CypherParser.PropertiesContext ctx) { return visitChildren(ctx); }
//    @Override public String visitNodePattern(CypherParser.NodePatternContext ctx) { return visitChildren(ctx); }
//    @Override public String visitAtom(CypherParser.AtomContext ctx) { return visitChildren(ctx); }
//    @Override public String visitLhs(CypherParser.LhsContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRelationshipPattern(CypherParser.RelationshipPatternContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRelationDetail(CypherParser.RelationDetailContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRelationshipTypes(CypherParser.RelationshipTypesContext ctx) { return visitChildren(ctx); }
//    @Override public String visitFunctionname(CypherParser.FunctionnameContext ctx) { return visitChildren(ctx); }
//    @Override public String visitParenthesizedExpression(CypherParser.ParenthesizedExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitFilterWith(CypherParser.FilterWithContext ctx) { return visitChildren(ctx); }
//    @Override public String visitPatternComprehension(CypherParser.PatternComprehensionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRelationshipsChainPattern(CypherParser.RelationshipsChainPatternContext ctx) { return visitChildren(ctx); }
//    @Override public String visitListComprehension(CypherParser.ListComprehensionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitFilterExpression(CypherParser.FilterExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitCountAll(CypherParser.CountAllContext ctx) { return visitChildren(ctx); }
//    @Override public String visitExpressionChain(CypherParser.ExpressionChainContext ctx) { return visitChildren(ctx); }
//    @Override public String visitCaseExpression(CypherParser.CaseExpressionContext ctx) { return visitChildren(ctx); }
//    @Override public String visitParameter(CypherParser.ParameterContext ctx) { return visitChildren(ctx); }
//    @Override public String visitLiteral(CypherParser.LiteralContext ctx) { return visitChildren(ctx); }
//    @Override public String visitRangeLit(CypherParser.RangeLitContext ctx) { return visitChildren(ctx); }
//    @Override public String visitNumLit(CypherParser.NumLitContext ctx) { return visitChildren(ctx); }
//    @Override public String visitCharLit(CypherParser.CharLitContext ctx) { return visitChildren(ctx); }
//    @Override public String visitMapLit(CypherParser.MapLitContext ctx) { return visitChildren(ctx); }
//    @Override public String visitMapPair(CypherParser.MapPairContext ctx) { return visitChildren(ctx); }
//    @Override public String visitName(CypherParser.NameContext ctx) { return visitChildren(ctx); }
//    @Override public String visitSymbol(CypherParser.SymbolContext ctx) { return visitChildren(ctx); }
//    @Override public String visitReservedWord(CypherParser.ReservedWordContext ctx) { return visitChildren(ctx); }

    public static String transform(CypherVisitorImpNoUpdate cv, String input){
        cv.Clear();
        CypherLexer lexer = new CypherLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CypherParser parser = new CypherParser(tokens);
        ParseTree tree = parser.script();

        parser.setBuildParseTree(true);
        cv.visit(tree);
        return cv.Getresult_qurey();
    }
    public static void main(String[] args) {
    }
}
