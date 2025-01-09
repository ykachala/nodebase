package io.nodebase.database;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QueryFilter {

    private static final Map<String, String> OPS = Map.of(
            "eq",       "=",
            "ne",       "!=",
            "gt",       ">",
            "lt",       "<",
            "gte",      ">=",
            "lte",      "<="
    );

    private final List<WhereClause> clauses = new ArrayList<>();
    private String orderBy;
    private String order;
    private int limit;
    private int offset;

    public static QueryFilter from(HttpServletRequest req) {
        QueryFilter f = new QueryFilter();
        f.orderBy = req.getParameter("orderBy");
        f.order = req.getParameter("order");

        String lim = req.getParameter("limit");
        String off = req.getParameter("offset");
        try { f.limit = lim != null ? Integer.parseInt(lim) : 100; } catch (NumberFormatException ignored) { f.limit = 100; }
        try { f.offset = off != null ? Integer.parseInt(off) : 0; } catch (NumberFormatException ignored) { f.offset = 0; }

        // Parse where[field][op]=value
        for (String param : req.getParameterMap().keySet()) {
            if (!param.startsWith("where[")) continue;
            // where[fieldName][op]
            int firstBracket = param.indexOf('[');
            int firstClose = param.indexOf(']', firstBracket);
            int secondBracket = param.indexOf('[', firstClose);
            int secondClose = param.indexOf(']', secondBracket);
            if (firstBracket < 0 || firstClose < 0 || secondBracket < 0 || secondClose < 0) continue;

            String field = param.substring(firstBracket + 1, firstClose);
            String op = param.substring(secondBracket + 1, secondClose);
            String value = req.getParameter(param);

            if (field.isBlank() || value == null) continue;

                // LIKE operator for substring matching
                if ("contains".equals(op)) {
                f.clauses.add(new WhereClause(field, "LIKE", "%" + value + "%", true));
            } else {
                String sqlOp = OPS.get(op);
                if (sqlOp != null) {
                    f.clauses.add(new WhereClause(field, sqlOp, value, false));
                }
            }
        }
        return f;
    }

    public void appendWhere(StringBuilder sb, List<Object> params) {
        for (WhereClause clause : clauses) {
            String safeField = clause.field().replaceAll("[^a-zA-Z0-9_]", "");
            if (safeField.isBlank()) continue;
            // Filter on JSON extracted field using SQLite's json_extract
            sb.append(" AND json_extract(data, '$.").append(safeField).append("') ")
              .append(clause.op()).append(" ?");
            params.add(clause.value());
        }
    }

    public String getOrderBy() { return orderBy; }
    public String getOrder() { return order; }
    public int getLimit() { return limit; }
    public int getOffset() { return offset; }

    private record WhereClause(String field, String op, String value, boolean isLike) {}
}
