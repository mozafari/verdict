package edu.umich.verdict.relation.expr;

import com.google.common.base.Optional;

import edu.umich.verdict.VerdictContext;
import edu.umich.verdict.parser.VerdictSQLBaseVisitor;
import edu.umich.verdict.parser.VerdictSQLParser;
import edu.umich.verdict.util.StringManipulations;

public class OrderByExpr extends Expr {
	
	private Expr expr;
	
	private Optional<String> direction;

	public OrderByExpr(VerdictContext vc, Expr expr, String direction) {
	    super(vc);
		this.expr = expr;
		this.direction = Optional.fromNullable(direction);
	}
	
	public OrderByExpr(VerdictContext vc, Expr expr) {
		this(vc, expr, null);
	}
	
	public Expr getExpression() {
		return expr;
	}
	
	public Optional<String> getDirection() {
		return direction;
	}
	
	public static OrderByExpr from(final VerdictContext vc, String expr) {
		VerdictSQLParser p = StringManipulations.parserOf(expr);
		VerdictSQLBaseVisitor<OrderByExpr> v = new VerdictSQLBaseVisitor<OrderByExpr>() {
			@Override
			public OrderByExpr visitOrder_by_expression(VerdictSQLParser.Order_by_expressionContext ctx) {
				String dir = (ctx.ASC() != null)? "ASC" : ((ctx.DESC() != null)? "DESC" : null);
				return new OrderByExpr(vc, Expr.from(vc, ctx.expression()), dir);
			}
		};
		return v.visit(p.order_by_expression());
	}
	
	@Override
	public String toString() {
		if (direction.isPresent()) {
			return expr.toString() + " " + direction.get();
		} else {
			return expr.toString();
		}
	}

	@Override
	public <T> T accept(ExprVisitor<T> v) {
		return v.call(this);
	}

	@Override
	public Expr withTableSubstituted(String newTab) {
		Expr newExpr = expr.withTableSubstituted(newTab);
		return new OrderByExpr(vc, newExpr, direction.orNull());
	}

	@Override
	public String toSql() {
		return toString();
	}

}
