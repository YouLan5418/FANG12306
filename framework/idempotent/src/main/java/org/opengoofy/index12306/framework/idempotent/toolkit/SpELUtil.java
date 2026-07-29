package org.opengoofy.index12306.framework.idempotent.toolkit;

import cn.hutool.core.util.ArrayUtil;
import com.google.common.collect.Lists;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;

/**
 * SpEL 表达式解析工具。
 */
public final class SpELUtil {

    private SpELUtil() {
    }

    public static Object parseKey(String spEl, Method method, Object[] contextObj) {
        ArrayList<String> spELFlag = Lists.newArrayList("#", "T(");
        Optional<String> optional = spELFlag.stream().filter(spEl::contains).findFirst();
        if (optional.isPresent()) {
            return parse(spEl, method, contextObj);
        }
        return spEl;
    }

    public static Object parse(String spEl, Method method, Object[] contextObj) {
        DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(spEl);
        String[] params = discoverer.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (ArrayUtil.isNotEmpty(params)) {
            for (int len = 0; len < params.length; len++) {
                context.setVariable(params[len], contextObj[len]);
            }
        }
        return exp.getValue(context);
    }
}
