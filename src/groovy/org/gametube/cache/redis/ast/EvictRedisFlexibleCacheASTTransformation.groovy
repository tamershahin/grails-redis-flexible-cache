/*
 * Copyright (c) 2013. Gametube SAS.
 */


package org.gametube.cache.redis.ast

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class EvictRedisFlexibleCacheASTTransformation extends AbstractRedisFlexibleASTTransformation {

    @Override
    protected void generateQuickCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map quickCacheProperties) {
        def keyString = astNodes[0]?.members?.key?.text

        if (!validateQuickCacheProperties(astNodes, sourceUnit, keyString)) {
            return
        }

        quickCacheProperties.put(KEY, keyString)
    }

    private Boolean validateQuickCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, keyString) {

        if (!keyString || keyString.class != String) {
            addError('Internal Error: annotation does not contain key property', astNodes[0], sourceUnit)
            return false
        }

        true
    }

    @Override
    protected ConstantExpression makeCacheServiceConstantExpression() {
        new ConstantExpression('evictCache')
    }

    @Override
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map quickCacheProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        addRedisServiceQuickCacheKeyExpression(quickCacheProperties, argumentListExpression)
        argumentListExpression
    }
}